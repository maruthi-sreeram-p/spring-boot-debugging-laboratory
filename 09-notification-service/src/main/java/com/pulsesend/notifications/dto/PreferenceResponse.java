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
public class PreferenceResponse {

    private Long id;
    private String recipientRef;
    private String channel;
    private boolean enabled;
    private Instant updatedAt;
}
