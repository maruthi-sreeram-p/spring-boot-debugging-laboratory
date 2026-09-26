package com.spicebox.ordering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "spicebox")
@Getter
@Setter
public class SpiceboxProperties {

    private Messaging messaging = new Messaging();
    private Ordering ordering = new Ordering();

    @Getter
    @Setter
    public static class Messaging {
        private String exchange = "food.orders";
        private String placedRoutingKey = "order.placed";
        private String readyRoutingKey = "order.ready-for-pickup";
        private String analyticsQueue = "analytics.orders";
        private String dispatchQueue = "delivery.dispatch";
    }

    @Getter
    @Setter
    public static class Ordering {
        private BigDecimal deliveryFee = new BigDecimal("39.00");
        private BigDecimal freeDeliveryThreshold = new BigDecimal("499.00");
        private BigDecimal minimumOrderValue = new BigDecimal("99.00");
    }
}
