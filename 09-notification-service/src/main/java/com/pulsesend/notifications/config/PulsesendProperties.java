package com.pulsesend.notifications.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pulsesend")
@Getter
@Setter
public class PulsesendProperties {

    private Messaging messaging = new Messaging();
    private Delivery delivery = new Delivery();

    @Getter
    @Setter
    public static class Messaging {
        private String exchange = "notifications.exchange";
        private String deadLetterExchange = "notifications.dlx";
        private String emailQueue = "notifications.email";
        private String smsQueue = "notifications.sms";
        private String inAppQueue = "notifications.inapp";
        private String deadLetterQueue = "notifications.dlq";
        private String routingPrefix = "notify";
    }

    @Getter
    @Setter
    public static class Delivery {
        private int maxAttempts = 5;
    }
}
