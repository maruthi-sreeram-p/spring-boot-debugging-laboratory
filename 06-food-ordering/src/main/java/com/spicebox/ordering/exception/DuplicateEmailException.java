package com.spicebox.ordering.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("An account already exists for " + email);
    }
}
