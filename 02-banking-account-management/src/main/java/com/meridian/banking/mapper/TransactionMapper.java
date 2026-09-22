package com.meridian.banking.mapper;

import com.meridian.banking.dto.TransactionResponse;
import com.meridian.banking.entity.AccountTransaction;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(AccountTransaction transaction) {
        String counterparty = transaction.getCounterpartyAccount() == null
                ? null
                : transaction.getCounterpartyAccount().getAccountNumber();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                counterparty,
                transaction.getDescription(),
                transaction.getCreatedAt());
    }

    public List<TransactionResponse> toResponses(List<AccountTransaction> transactions) {
        return transactions.stream().map(this::toResponse).toList();
    }
}
