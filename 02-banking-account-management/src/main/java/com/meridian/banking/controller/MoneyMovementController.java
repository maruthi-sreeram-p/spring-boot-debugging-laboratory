package com.meridian.banking.controller;

import com.meridian.banking.dto.AmountRequest;
import com.meridian.banking.dto.TransactionResponse;
import com.meridian.banking.dto.TransferRequest;
import com.meridian.banking.dto.TransferResponse;
import com.meridian.banking.security.AuthenticatedCustomer;
import com.meridian.banking.service.AccountTransactionService;
import com.meridian.banking.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MoneyMovementController {

    private final AccountTransactionService accountTransactionService;
    private final TransferService transferService;

    public MoneyMovementController(AccountTransactionService accountTransactionService,
                                   TransferService transferService) {
        this.accountTransactionService = accountTransactionService;
        this.transferService = transferService;
    }

    @PostMapping("/accounts/{accountId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                       @PathVariable Long accountId,
                                       @Valid @RequestBody AmountRequest request) {
        return accountTransactionService.deposit(principal.getCustomerId(), accountId, request);
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                        @PathVariable Long accountId,
                                        @Valid @RequestBody AmountRequest request) {
        return accountTransactionService.withdraw(principal.getCustomerId(), accountId, request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                     @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(principal.getCustomerId(), request);
    }
}
