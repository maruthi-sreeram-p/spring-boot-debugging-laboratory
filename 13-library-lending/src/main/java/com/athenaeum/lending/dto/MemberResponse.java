package com.athenaeum.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class MemberResponse {

    private final Long id;
    private final String membershipNumber;
    private final String fullName;
    private final String email;
    private final String tier;
    private final String status;
    private final int activeLoanCount;
    private final int loanLimit;
    private final BigDecimal outstandingFines;
    private final LocalDate joinedOn;
}
