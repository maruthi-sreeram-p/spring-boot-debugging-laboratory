package com.aegis.identity.security;

import com.aegis.identity.config.IdentityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints and parses the two kinds of token the platform issues.
 *
 * An access token is short lived and carries everything a resource server needs to make an
 * authorization decision without calling back here. A refresh token is long lived, carries
 * nothing but an identity, and is tracked in the database so it can be revoked.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_EMAIL = "email";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final IdentityProperties properties;
    private final SecretKey signingKey;

    public JwtService(IdentityProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getJwt().getSigningSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issueAccessToken(Long accountId, String email,
                                        List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.getJwt().getAccessTokenMinutes()));
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(String.valueOf(accountId))
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PERMISSIONS, permissions)
                .signWith(signingKey)
                .compact();

        log.debug("Issued access token {} for account {} expiring at {}", tokenId, accountId, expiresAt);
        return new IssuedToken(token, tokenId, expiresAt);
    }

    public IssuedToken issueRefreshToken(Long accountId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(properties.getJwt().getRefreshTokenDays()));
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(String.valueOf(accountId))
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .claim(CLAIM_EMAIL, email)
                .signWith(signingKey)
                .compact();

        log.debug("Issued refresh token {} for account {} expiring at {}", tokenId, accountId, expiresAt);
        return new IssuedToken(token, tokenId, expiresAt);
    }

    /**
     * Verifies the signature, the issuer and the expiry. Anything that fails throws.
     */
    public Claims parse(String token) throws JwtException {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    @SuppressWarnings("unchecked")
    public List<String> stringListClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public record IssuedToken(String value, String tokenId, Instant expiresAt) {
    }
}
