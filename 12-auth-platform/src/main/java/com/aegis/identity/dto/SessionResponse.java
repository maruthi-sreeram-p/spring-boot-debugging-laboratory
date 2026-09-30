package com.aegis.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class SessionResponse {

    private final Long id;
    private final String tokenId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final boolean revoked;
    private final String replacedBy;
    private final String userAgent;
}
