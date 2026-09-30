package com.aegis.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class DocumentResponse {

    private final Long id;
    private final String reference;
    private final String title;
    private final String body;
    private final Long ownerId;
    private final String ownerEmail;
    private final String sensitivity;
    private final Instant updatedAt;
}
