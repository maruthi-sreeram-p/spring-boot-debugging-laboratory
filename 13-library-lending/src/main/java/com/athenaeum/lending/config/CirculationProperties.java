package com.athenaeum.lending.config;

import com.athenaeum.lending.entity.MemberTier;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "athenaeum")
public class CirculationProperties {

    private final Circulation circulation = new Circulation();
    private final Fines fines = new Fines();

    @Getter
    @Setter
    public static class Circulation {
        private int standardLoanDays = 14;
        private int premiumLoanDays = 21;
        private int staffLoanDays = 28;
        private int standardLoanLimit = 4;
        private int premiumLoanLimit = 8;
        private int staffLoanLimit = 12;
        private int maxRenewals = 2;
        private int holdShelfHours = 48;

        public int loanDaysFor(MemberTier tier) {
            return switch (tier) {
                case PREMIUM -> premiumLoanDays;
                case STAFF -> staffLoanDays;
                default -> standardLoanDays;
            };
        }

        public int loanLimitFor(MemberTier tier) {
            return switch (tier) {
                case PREMIUM -> premiumLoanLimit;
                case STAFF -> staffLoanLimit;
                default -> standardLoanLimit;
            };
        }
    }

    @Getter
    @Setter
    public static class Fines {
        private BigDecimal dailyRate = new BigDecimal("5.00");
        private BigDecimal maximum = new BigDecimal("500.00");
        private String accrualCron = "0 0 2 * * *";
    }
}
