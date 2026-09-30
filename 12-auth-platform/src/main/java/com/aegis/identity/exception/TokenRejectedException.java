package com.aegis.identity.exception;

public class TokenRejectedException extends RuntimeException {

    public TokenRejectedException(String reason) {
        super(reason);
    }
}
