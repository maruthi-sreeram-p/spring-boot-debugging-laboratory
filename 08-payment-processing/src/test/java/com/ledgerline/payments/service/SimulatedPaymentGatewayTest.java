package com.ledgerline.payments.service;

import com.ledgerline.payments.config.PaymentProperties;
import com.ledgerline.payments.gateway.GatewayResult;
import com.ledgerline.payments.gateway.GatewayTimeoutException;
import com.ledgerline.payments.gateway.SimulatedPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedPaymentGatewayTest {

    private SimulatedPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties();
        properties.getGateway().setSlowResponseMillis(5);
        gateway = new SimulatedPaymentGateway(properties);
    }

    @Test
    void approvesOrdinaryAmounts() {
        GatewayResult result = gateway.capture("PAY-1", new BigDecimal("1299.00"), "CARD_VISA");

        assertThat(result.approved()).isTrue();
        assertThat(result.gatewayRef()).startsWith("SIMGW-");
    }

    @Test
    void declinesTheSandboxDeclineAmount() {
        GatewayResult result = gateway.capture("PAY-2", new BigDecimal("760.11"), "CARD_VISA");

        assertThat(result.approved()).isFalse();
        assertThat(result.message()).isEqualTo("Insufficient funds");
    }

    @Test
    void recordsTheCaptureEvenWhenTheResponseIsLost() {
        assertThatThrownBy(() -> gateway.capture("PAY-3", new BigDecimal("1120.22"), "CARD_VISA"))
                .isInstanceOf(GatewayTimeoutException.class);

        assertThat(gateway.lookup("PAY-3")).hasSize(1);
        assertThat(gateway.lookup("PAY-3").get(0).outcome()).isEqualTo("CAPTURED");
    }
}
