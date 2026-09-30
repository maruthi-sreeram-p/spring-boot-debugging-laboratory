package com.aegis.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AuditEventResponse {

    private final Long id;
    private final Long accountId;
    private final String eventType;
    private final String detail;
    private final Instant createdAt;
}
