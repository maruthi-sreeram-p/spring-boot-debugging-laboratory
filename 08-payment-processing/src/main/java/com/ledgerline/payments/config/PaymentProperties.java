package com.ledgerline.payments.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payments")
@Getter
@Setter
public class PaymentProperties {

    private Gateway gateway = new Gateway();
    private Retry retry = new Retry();
    private Reconciliation reconciliation = new Reconciliation();

    @Getter
    @Setter
    public static class Gateway {
        private String name = "simulator";
        private long slowResponseMillis = 900;
        private String approvalPrefix = "SIMGW";
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
        private long backoffMillis = 200;
    }

    @Getter
    @Setter
    public static class Reconciliation {
        private int staleAfterMinutes = 5;
        private String cron = "0 */2 * * * *";
    }
}
