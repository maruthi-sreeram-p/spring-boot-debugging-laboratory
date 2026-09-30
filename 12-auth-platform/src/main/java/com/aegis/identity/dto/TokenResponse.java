package com.aegis.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class TokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final Instant accessTokenExpiresAt;
    private final Instant refreshTokenExpiresAt;
    private final List<String> roles;
    private final List<String> permissions;
}
