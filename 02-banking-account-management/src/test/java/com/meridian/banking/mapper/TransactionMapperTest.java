package com.meridian.banking.mapper;

import com.meridian.banking.dto.TransactionResponse;
import com.meridian.banking.entity.Account;
import com.meridian.banking.entity.AccountTransaction;
import com.meridian.banking.entity.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

    private final TransactionMapper mapper = new TransactionMapper();

    @Test
    void mapsTransferLegWithCounterpartyAccountNumber() {
        Account own = new Account();
        own.setId(1L);
        own.setAccountNumber("MB-0000-1001");

        Account other = new Account();
        other.setId(3L);
        other.setAccountNumber("MB-0000-1003");

        AccountTransaction transaction = new AccountTransaction();
        transaction.setId(3L);
        transaction.setReference("TRF-20240908-000003");
        transaction.setAccount(own);
        transaction.setCounterpartyAccount(other);
        transaction.setType(TransactionType.TRANSFER_OUT);
        transaction.setAmount(new BigDecimal("5000.00"));
        transaction.setBalanceAfter(new BigDecimal("37000.00"));
        transaction.setDescription("Rent share");
        transaction.setCreatedAt(Instant.parse("2024-09-08T06:45:00Z"));

        TransactionResponse response = mapper.toResponse(transaction);

        assertThat(response.getReference()).isEqualTo("TRF-20240908-000003");
        assertThat(response.getType()).isEqualTo("TRANSFER_OUT");
        assertThat(response.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.getBalanceAfter()).isEqualByComparingTo("37000.00");
        assertThat(response.getCounterpartyAccountNumber()).isEqualTo("MB-0000-1003");
    }

    @Test
    void leavesCounterpartyNullForCashTransactions() {
        Account own = new Account();
        own.setId(1L);
        own.setAccountNumber("MB-0000-1001");

        AccountTransaction transaction = new AccountTransaction();
        transaction.setId(1L);
        transaction.setReference("DEP-20240902-000001");
        transaction.setAccount(own);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(new BigDecimal("50000.00"));
        transaction.setBalanceAfter(new BigDecimal("50000.00"));
        transaction.setCreatedAt(Instant.parse("2024-09-02T04:31:00Z"));

        assertThat(mapper.toResponse(transaction).getCounterpartyAccountNumber()).isNull();
    }
}
