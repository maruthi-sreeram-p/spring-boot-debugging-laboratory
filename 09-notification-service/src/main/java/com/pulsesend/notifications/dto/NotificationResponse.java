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
public class NotificationResponse {

    private Long id;
    private String notificationRef;
    private String recipientRef;
    private String destination;
    private String channel;
    private String templateCode;
    private String payload;
    private String status;
    private Integer attempts;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
}
