package com.aegis.identity.controller;

import com.aegis.identity.dto.AccountResponse;
import com.aegis.identity.dto.AuditEventResponse;
import com.aegis.identity.security.AegisPrincipal;
import com.aegis.identity.security.TokenDenylist;
import com.aegis.identity.service.AccountService;
import com.aegis.identity.service.AuditService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The back office. Every route here is reserved for administrators by the rules in
 * {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AccountService accountService;
    private final AuditService auditService;
    private final TokenDenylist denylist;

    public AdminController(AccountService accountService,
                           AuditService auditService,
                           TokenDenylist denylist) {
        this.accountService = accountService;
        this.auditService = auditService;
        this.denylist = denylist;
    }

    @GetMapping("/accounts")
    public List<AccountResponse> accounts() {
        return accountService.all();
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse account(@PathVariable Long accountId) {
        return accountService.byId(accountId);
    }

    @PostMapping("/accounts/{accountId}/lock")
    public AccountResponse lock(@AuthenticationPrincipal AegisPrincipal principal,
                                @PathVariable Long accountId) {
        return accountService.lock(accountId, principal.email());
    }

    @PostMapping("/accounts/{accountId}/unlock")
    public AccountResponse unlock(@AuthenticationPrincipal AegisPrincipal principal,
                                  @PathVariable Long accountId) {
        return accountService.unlock(accountId, principal.email());
    }

    @PostMapping("/accounts/{accountId}/roles/{roleName}")
    public AccountResponse grantRole(@AuthenticationPrincipal AegisPrincipal principal,
                                     @PathVariable Long accountId,
                                     @PathVariable String roleName) {
        return accountService.grantRole(accountId, roleName, principal.email());
    }

    @DeleteMapping("/accounts/{accountId}/roles/{roleName}")
    public AccountResponse revokeRole(@AuthenticationPrincipal AegisPrincipal principal,
                                      @PathVariable Long accountId,
                                      @PathVariable String roleName) {
        return accountService.revokeRole(accountId, roleName, principal.email());
    }

    @GetMapping("/audit")
    public List<AuditEventResponse> audit(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return auditService.recent(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/denylist")
    public Set<String> denylist() {
        return denylist.entries();
    }

    @DeleteMapping("/denylist")
    public Map<String, Long> clearDenylist() {
        return Map.of("removed", denylist.clear());
    }
}
