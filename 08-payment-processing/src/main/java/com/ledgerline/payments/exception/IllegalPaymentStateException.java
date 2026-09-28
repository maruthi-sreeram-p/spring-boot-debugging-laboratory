package com.ledgerline.payments.exception;

public class IllegalPaymentStateException extends RuntimeException {

    public IllegalPaymentStateException(String message) {
        super(message);
    }
}
