package com.pulsesend.notifications.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private String notificationRef;
    private String recipientRef;
    private String destination;
    private String channel;
    private String templateCode;
    private String payload;
    private Instant queuedAt;
}
