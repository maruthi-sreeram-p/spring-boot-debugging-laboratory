package com.pulsesend.notifications.channel;

public record DeliveryOutcome(boolean delivered, String detail) {

    public static DeliveryOutcome delivered(String detail) {
        return new DeliveryOutcome(true, detail);
    }

    public static DeliveryOutcome rejected(String detail) {
        return new DeliveryOutcome(false, detail);
    }
}
