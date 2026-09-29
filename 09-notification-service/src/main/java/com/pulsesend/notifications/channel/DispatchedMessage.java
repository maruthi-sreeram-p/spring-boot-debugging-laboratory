package com.pulsesend.notifications.channel;

import java.time.Instant;

public record DispatchedMessage(String notificationRef,
                                String channel,
                                String destination,
                                String subject,
                                String body,
                                Instant sentAt) {
}
