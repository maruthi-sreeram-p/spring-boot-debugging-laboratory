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
public class OrderResponse {

    private Long id;
    private String orderRef;
    private String merchantCode;
    private String customerRef;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
