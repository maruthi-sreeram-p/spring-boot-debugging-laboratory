package com.aegis.identity.service;

import com.aegis.identity.dto.AccountResponse;
import com.aegis.identity.entity.Account;
import com.aegis.identity.entity.AccountStatus;
import com.aegis.identity.entity.Role;
import com.aegis.identity.exception.ResourceNotFoundException;
import com.aegis.identity.mapper.IdentityMapper;
import com.aegis.identity.repository.AccountRepository;
import com.aegis.identity.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The administrative view of an account: who it is, what it can do, and whether it is allowed
 * to sign in.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final IdentityMapper identityMapper;

    public AccountService(AccountRepository accountRepository,
                          RoleRepository roleRepository,
                          AuditService auditService,
                          IdentityMapper identityMapper) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.identityMapper = identityMapper;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> all() {
        return identityMapper.toAccountResponses(accountRepository.findAll());
    }

    @Transactional(readOnly = true)
    public AccountResponse byId(Long accountId) {
        return identityMapper.toResponse(load(accountId));
    }

    @Transactional
    public AccountResponse lock(Long accountId, String actor) {
        Account account = load(accountId);
        account.setStatus(AccountStatus.LOCKED);
        auditService.record(accountId, "ACCOUNT_LOCKED", "Locked by " + actor);
        log.info("Account {} locked by {}", account.getEmail(), actor);
        return identityMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse unlock(Long accountId, String actor) {
        Account account = load(accountId);
        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedAttempts(0);
        auditService.record(accountId, "ACCOUNT_UNLOCKED", "Unlocked by " + actor);
        log.info("Account {} unlocked by {}", account.getEmail(), actor);
        return identityMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse grantRole(Long accountId, String roleName, String actor) {
        Account account = load(accountId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleName));
        account.getRoles().add(role);
        auditService.record(accountId, "ROLE_GRANTED", roleName + " granted by " + actor);
        log.info("Role {} granted to {} by {}", roleName, account.getEmail(), actor);
        return identityMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse revokeRole(Long accountId, String roleName, String actor) {
        Account account = load(accountId);
        account.getRoles().removeIf(role -> role.getName().equals(roleName));
        auditService.record(accountId, "ROLE_REVOKED", roleName + " revoked by " + actor);
        log.info("Role {} revoked from {} by {}", roleName, account.getEmail(), actor);
        return identityMapper.toResponse(account);
    }

    private Account load(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }
}
