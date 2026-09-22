package com.meridian.banking.mapper;

import com.meridian.banking.dto.AccountResponse;
import com.meridian.banking.entity.Account;
import com.meridian.banking.entity.AccountStatus;
import com.meridian.banking.entity.AccountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    void mapsAccountFieldsForTheCustomerFacingApi() {
        Account account = new Account();
        account.setId(3L);
        account.setAccountNumber("MB-0000-1003");
        account.setAccountType(AccountType.CHECKING);
        account.setCurrency("INR");
        account.setBalance(new BigDecimal("17640.50"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setOpenedAt(Instant.parse("2023-06-19T14:10:00Z"));

        AccountResponse response = mapper.toResponse(account);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getAccountNumber()).isEqualTo("MB-0000-1003");
        assertThat(response.getAccountType()).isEqualTo("CHECKING");
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getBalance()).isEqualByComparingTo("17640.50");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getOpenedAt()).isEqualTo(Instant.parse("2023-06-19T14:10:00Z"));
    }
}
