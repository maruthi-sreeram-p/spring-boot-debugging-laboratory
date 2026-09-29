package com.pulsesend.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryLogResponse {

    private Long id;
    private Integer attemptNo;
    private String outcome;
    private String detail;
    private Instant createdAt;
}
