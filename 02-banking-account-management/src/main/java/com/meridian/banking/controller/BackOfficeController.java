package com.meridian.banking.controller;

import com.meridian.banking.dto.AccountResponse;
import com.meridian.banking.service.AccountService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/back-office")
public class BackOfficeController {

    private final AccountService accountService;

    public BackOfficeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts/{accountNumber}/freeze")
    public AccountResponse freeze(@PathVariable String accountNumber) {
        return accountService.freezeAccount(accountNumber);
    }
}
