package com.aegis.identity.exception;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String status) {
        super("Account is not active, current status is " + status);
    }
}
