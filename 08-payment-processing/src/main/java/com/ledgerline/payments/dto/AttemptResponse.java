package com.ledgerline.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttemptResponse {

    private Long id;
    private Integer attemptNo;
    private String outcome;
    private String gatewayRef;
    private String message;
    private Instant createdAt;
}
