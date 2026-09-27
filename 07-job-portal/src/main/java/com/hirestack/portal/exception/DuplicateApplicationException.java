package com.hirestack.portal.exception;

public class DuplicateApplicationException extends RuntimeException {

    public DuplicateApplicationException(String reference) {
        super("You have already applied to " + reference);
    }
}
