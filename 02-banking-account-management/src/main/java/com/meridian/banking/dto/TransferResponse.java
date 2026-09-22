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
public class TransferResponse {

    private String reference;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private BigDecimal sourceBalanceAfter;
    private String description;
    private Instant executedAt;
}
