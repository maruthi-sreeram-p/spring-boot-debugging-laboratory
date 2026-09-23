package com.harbourview.clinic.exception;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String from, String to) {
        super("An appointment that is " + from + " cannot be moved to " + to);
    }
}
