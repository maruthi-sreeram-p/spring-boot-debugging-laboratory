package com.meridian.banking.controller;

import com.meridian.banking.dto.AccountResponse;
import com.meridian.banking.dto.OpenAccountRequest;
import com.meridian.banking.dto.PagedResponse;
import com.meridian.banking.dto.TransactionResponse;
import com.meridian.banking.security.AuthenticatedCustomer;
import com.meridian.banking.service.AccountService;
import com.meridian.banking.service.StatementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final StatementService statementService;

    public AccountController(AccountService accountService, StatementService statementService) {
        this.accountService = accountService;
        this.statementService = statementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                @Valid @RequestBody OpenAccountRequest request) {
        return accountService.openAccount(principal.getCustomerId(), request);
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return accountService.listAccounts(principal.getCustomerId());
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedCustomer principal,
                               @PathVariable Long accountId) {
        return accountService.getAccount(accountId, principal.getCustomerId());
    }

    @GetMapping("/{accountId}/transactions")
    public PagedResponse<TransactionResponse> statement(
            @AuthenticationPrincipal AuthenticatedCustomer principal,
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return statementService.statement(principal.getCustomerId(), accountId, from, to, pageable);
    }
}
