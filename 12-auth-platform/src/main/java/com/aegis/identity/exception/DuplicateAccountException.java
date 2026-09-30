package com.aegis.identity.exception;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String email) {
        super("An account already exists for " + email);
    }
}
