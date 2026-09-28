package com.ledgerline.payments.gateway;

public class GatewayTimeoutException extends RuntimeException {

    public GatewayTimeoutException(String message) {
        super(message);
    }
}
