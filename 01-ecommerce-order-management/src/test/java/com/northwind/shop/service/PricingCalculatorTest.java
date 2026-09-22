package com.northwind.shop.service;

import com.northwind.shop.config.ShopProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculatorTest {

    private PricingCalculator calculator;

    @BeforeEach
    void setUp() {
        ShopProperties properties = new ShopProperties();
        properties.getPricing().setFreeShippingThreshold(new BigDecimal("500.00"));
        properties.getPricing().setStandardShippingFee(new BigDecimal("49.00"));
        calculator = new PricingCalculator(properties);
    }

    @Test
    @DisplayName("line total multiplies unit price by quantity at two decimals")
    void lineTotalIsRoundedToTwoDecimals() {
        assertThat(calculator.lineTotal(new BigDecimal("129.00"), 3))
                .isEqualByComparingTo("387.00");
        assertThat(calculator.lineTotal(new BigDecimal("19.99"), 7))
                .isEqualByComparingTo("139.93");
    }

    @Test
    @DisplayName("shipping is charged below the free shipping threshold")
    void shippingChargedBelowThreshold() {
        assertThat(calculator.shippingFeeFor(new BigDecimal("238.00")))
                .isEqualByComparingTo("49.00");
    }

    @Test
    @DisplayName("shipping is waived at or above the free shipping threshold")
    void shippingWaivedAtThreshold() {
        assertThat(calculator.shippingFeeFor(new BigDecimal("500.00")))
                .isEqualByComparingTo("0.00");
        assertThat(calculator.shippingFeeFor(new BigDecimal("1378.00")))
                .isEqualByComparingTo("0.00");
    }
}
