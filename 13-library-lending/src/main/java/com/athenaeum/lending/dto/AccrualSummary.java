package com.athenaeum.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AccrualSummary {

    private final LocalDateTime ranAt;
    private final int loansExamined;
    private final int finesCreated;
    private final int finesUpdated;
    private final BigDecimal totalAssessed;
}
