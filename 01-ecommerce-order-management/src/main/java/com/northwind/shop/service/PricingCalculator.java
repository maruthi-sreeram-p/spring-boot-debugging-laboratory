package com.northwind.shop.service;

import com.northwind.shop.config.ShopProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single place where money is rounded, so that the cart preview and the persisted
 * order always agree on the numbers shown to the customer.
 */
@Component
public class PricingCalculator {

    private static final int MONEY_SCALE = 2;

    private final ShopProperties properties;

    public PricingCalculator(ShopProperties properties) {
        this.properties = properties;
    }

    public BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal shippingFeeFor(BigDecimal subtotal) {
        BigDecimal threshold = properties.getPricing().getFreeShippingThreshold();
        if (subtotal.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        }
        return properties.getPricing().getStandardShippingFee()
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
