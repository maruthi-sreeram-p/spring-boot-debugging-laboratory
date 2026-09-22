package com.meridian.banking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "banking")
@Getter
@Setter
public class BankingProperties {

    private Transfer transfer = new Transfer();
    private AccountSettings account = new AccountSettings();

    @Getter
    @Setter
    public static class Transfer {
        private BigDecimal dailyLimit = new BigDecimal("50000.00");
        private String referencePrefix = "TRF";
    }

    @Getter
    @Setter
    public static class AccountSettings {
        private String numberPrefix = "MB";
        private BigDecimal minimumOpeningBalance = BigDecimal.ZERO;
    }
}
