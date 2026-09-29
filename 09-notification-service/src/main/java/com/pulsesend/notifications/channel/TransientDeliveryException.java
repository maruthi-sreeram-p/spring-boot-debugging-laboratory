package com.pulsesend.notifications.channel;

public class TransientDeliveryException extends RuntimeException {

    public TransientDeliveryException(String message) {
        super(message);
    }
}
