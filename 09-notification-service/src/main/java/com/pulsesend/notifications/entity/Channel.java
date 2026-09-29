package com.pulsesend.notifications.entity;

public enum Channel {
    EMAIL,
    SMS,
    IN_APP;

    /**
     * The segment this channel contributes to a routing key.
     */
    public String routingSegment() {
        return switch (this) {
            case EMAIL -> "email";
            case SMS -> "sms";
            case IN_APP -> "inapp";
        };
    }
}
