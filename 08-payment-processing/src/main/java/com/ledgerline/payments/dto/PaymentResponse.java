package com.ledgerline.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String paymentRef;
    private String orderRef;
    private String idempotencyKey;
    private BigDecimal amount;
    private String currency;
    private String instrument;
    private String status;
    private Integer attempts;
    private String gatewayRef;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
}
