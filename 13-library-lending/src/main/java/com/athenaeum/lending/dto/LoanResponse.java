package com.athenaeum.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LoanResponse {

    private final Long id;
    private final Long copyId;
    private final String barcode;
    private final Long bookId;
    private final String title;
    private final Long memberId;
    private final String membershipNumber;
    private final LocalDateTime borrowedAt;
    private final LocalDateTime dueAt;
    private final LocalDateTime returnedAt;
    private final String status;
    private final int renewalCount;
    private final boolean overdue;
}
