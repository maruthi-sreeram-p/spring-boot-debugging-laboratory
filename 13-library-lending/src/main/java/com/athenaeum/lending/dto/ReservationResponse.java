package com.athenaeum.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ReservationResponse {

    private final Long id;
    private final Long bookId;
    private final String title;
    private final Long memberId;
    private final String membershipNumber;
    private final LocalDateTime placedAt;
    private final String status;
    private final int queuePosition;
    private final Long heldCopyId;
    private final LocalDateTime readyUntil;
}
