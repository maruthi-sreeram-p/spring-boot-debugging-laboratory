package com.aegis.identity.service;

import com.aegis.identity.config.IdentityProperties;
import com.aegis.identity.dto.LoginRequest;
import com.aegis.identity.dto.RefreshRequest;
import com.aegis.identity.dto.RegisterRequest;
import com.aegis.identity.dto.SessionResponse;
import com.aegis.identity.dto.TokenResponse;
import com.aegis.identity.entity.Account;
import com.aegis.identity.entity.AccountStatus;
import com.aegis.identity.entity.RefreshToken;
import com.aegis.identity.entity.Role;
import com.aegis.identity.exception.AccountNotActiveException;
import com.aegis.identity.exception.DuplicateAccountException;
import com.aegis.identity.exception.InvalidCredentialsException;
import com.aegis.identity.exception.ResourceNotFoundException;
import com.aegis.identity.exception.TokenRejectedException;
import com.aegis.identity.mapper.IdentityMapper;
import com.aegis.identity.repository.AccountRepository;
import com.aegis.identity.repository.RefreshTokenRepository;
import com.aegis.identity.repository.RoleRepository;
import com.aegis.identity.security.JwtService;
import com.aegis.identity.security.TokenDenylist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Registration, sign in, token rotation and sign out.
 *
 * Access tokens are never stored. Refresh tokens are stored as a hash alongside their jti so a
 * session can be listed and revoked without the platform ever holding the token itself.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_ROLE = "ROLE_VIEWER";

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenDenylist denylist;
    private final AuditService auditService;
    private final IdentityMapper identityMapper;
    private final IdentityProperties properties;

    public AuthService(AccountRepository accountRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenDenylist denylist,
                       AuditService auditService,
                       IdentityMapper identityMapper,
                       IdentityProperties properties) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.denylist = denylist;
        this.auditService = auditService;
        this.identityMapper = identityMapper;
        this.properties = properties;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request, String userAgent) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateAccountException(email);
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Role", DEFAULT_ROLE));

        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setDisplayName(request.getDisplayName().trim());
        account.setStatus(AccountStatus.ACTIVE);
        account.getRoles().add(defaultRole);

        Account saved = accountRepository.save(account);
        auditService.record(saved.getId(), "ACCOUNT_CREATED", "Self registration for " + saved.getEmail());
        log.info("Account {} registered", saved.getEmail());

        return issue(saved, userAgent).response();
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String userAgent) {
        String email = request.getEmail().trim();
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> {
                    auditService.record(null, "LOGIN_FAILED", "Unknown account: " + email);
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            account.setFailedAttempts(account.getFailedAttempts() + 1);
            if (account.getFailedAttempts() >= properties.getLockout().getMaxFailedAttempts()) {
                account.setStatus(AccountStatus.LOCKED);
                auditService.record(account.getId(), "ACCOUNT_LOCKED",
                        "Locked after " + account.getFailedAttempts() + " failed sign-in attempts");
            }
            auditService.record(account.getId(), "LOGIN_FAILED", "Bad password for " + account.getEmail());
            throw new InvalidCredentialsException();
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            auditService.record(account.getId(), "LOGIN_BLOCKED",
                    "Sign in refused, status is " + account.getStatus());
            throw new AccountNotActiveException(account.getStatus().name());
        }

        account.setFailedAttempts(0);
        account.setLastLoginAt(Instant.now());
        auditService.record(account.getId(), "LOGIN_SUCCEEDED", "Signed in as " + account.getEmail());
        log.info("Account {} signed in", account.getEmail());

        return issue(account, userAgent).response();
    }

    /**
     * Exchanges a refresh token for a new pair. The presented token is rotated out: it is marked
     * as replaced so that a stolen copy can be spotted in the session list.
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request, String userAgent) {
        Claims claims = parseRefreshToken(request.getRefreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenId(claims.getId())
                .orElseThrow(() -> new TokenRejectedException("Refresh token is not recognised"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenRejectedException("Refresh token has expired");
        }

        if (!hash(request.getRefreshToken()).equals(stored.getTokenHash())) {
            throw new TokenRejectedException("Refresh token does not match the stored session");
        }

        Account account = accountRepository.findById(stored.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", stored.getAccountId()));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getStatus().name());
        }

        IssuedPair issued = issue(account, userAgent);

        stored.setRevoked(true);
        stored.setReplacedBy(issued.refreshTokenId());
        auditService.record(account.getId(), "TOKEN_REFRESHED",
                "Session " + stored.getTokenId() + " rotated");
        log.info("Refresh token {} rotated for account {}", stored.getTokenId(), account.getEmail());

        return issued.response();
    }

    @Transactional
    public void logout(RefreshRequest request) {
        Claims claims = parseRefreshToken(request.getRefreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenId(claims.getId())
                .orElseThrow(() -> new TokenRejectedException("Refresh token is not recognised"));

        stored.setRevoked(true);
        denylist.add(claims.getId(), claims.getExpiration().toInstant());

        auditService.record(stored.getAccountId(), "LOGOUT", "Session " + stored.getTokenId() + " ended");
        log.info("Session {} ended for account {}", stored.getTokenId(), stored.getAccountId());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(Long accountId) {
        return identityMapper.toSessionResponses(
                refreshTokenRepository.findByAccountIdOrderByIssuedAtDesc(accountId));
    }

    private IssuedPair issue(Account account, String userAgent) {
        List<String> roleNames = identityMapper.roleNames(account);
        List<String> permissions = identityMapper.permissionNames(account);
        List<String> shortRoleNames = roleNames.stream()
                .map(name -> name.startsWith("ROLE_") ? name.substring("ROLE_".length()) : name)
                .toList();

        JwtService.IssuedToken access =
                jwtService.issueAccessToken(account.getId(), account.getEmail(), shortRoleNames, permissions);
        JwtService.IssuedToken refresh =
                jwtService.issueRefreshToken(account.getId(), account.getEmail());

        RefreshToken record = new RefreshToken();
        record.setAccountId(account.getId());
        record.setTokenId(refresh.tokenId());
        record.setTokenHash(hash(refresh.value()));
        record.setIssuedAt(Instant.now());
        record.setExpiresAt(refresh.expiresAt());
        record.setUserAgent(userAgent == null ? "unknown" : userAgent.substring(0, Math.min(userAgent.length(), 200)));
        refreshTokenRepository.save(record);

        TokenResponse response = new TokenResponse(access.value(), refresh.value(), "Bearer",
                access.expiresAt(), refresh.expiresAt(), roleNames, permissions);
        return new IssuedPair(response, refresh.tokenId());
    }

    private record IssuedPair(TokenResponse response, String refreshTokenId) {
    }

    private Claims parseRefreshToken(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new TokenRejectedException("Refresh token is not valid: " + exception.getMessage());
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class))) {
            throw new TokenRejectedException("Presented token is not a refresh token");
        }
        return claims;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
