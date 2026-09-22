package com.northwind.shop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "shop")
@Getter
@Setter
public class ShopProperties {

    private Cart cart = new Cart();
    private Pricing pricing = new Pricing();

    @Getter
    @Setter
    public static class Cart {
        private String keyPrefix = "shop:cart:";
        private int ttlHours = 72;
    }

    @Getter
    @Setter
    public static class Pricing {
        private BigDecimal freeShippingThreshold = new BigDecimal("500.00");
        private BigDecimal standardShippingFee = new BigDecimal("49.00");
    }
}
