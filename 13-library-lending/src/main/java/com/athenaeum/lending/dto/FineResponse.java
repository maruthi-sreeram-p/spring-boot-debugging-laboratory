package com.athenaeum.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FineResponse {

    private final Long id;
    private final Long loanId;
    private final Long memberId;
    private final BigDecimal amount;
    private final int daysOverdue;
    private final LocalDateTime assessedAt;
    private final LocalDateTime paidAt;
    private final String status;
}
