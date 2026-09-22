package com.meridian.banking.dto;

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
public class TransactionResponse {

    private Long id;
    private String reference;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String counterpartyAccountNumber;
    private String description;
    private Instant createdAt;
}
