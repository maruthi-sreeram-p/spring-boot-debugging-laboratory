package com.ledgerline.payments.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewayCall(String paymentRef,
                          BigDecimal amount,
                          String instrument,
                          String outcome,
                          String gatewayRef,
                          Instant receivedAt) {
}
