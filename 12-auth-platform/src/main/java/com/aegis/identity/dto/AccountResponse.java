package com.aegis.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class AccountResponse {

    private final Long id;
    private final String email;
    private final String displayName;
    private final String status;
    private final int failedAttempts;
    private final List<String> roles;
    private final List<String> permissions;
    private final Instant createdAt;
    private final Instant lastLoginAt;
}
