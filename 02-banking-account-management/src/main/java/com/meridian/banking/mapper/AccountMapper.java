package com.meridian.banking.mapper;

import com.meridian.banking.dto.AccountResponse;
import com.meridian.banking.entity.Account;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus().name(),
                account.getOpenedAt());
    }

    public List<AccountResponse> toResponses(List<Account> accounts) {
        return accounts.stream().map(this::toResponse).toList();
    }
}
