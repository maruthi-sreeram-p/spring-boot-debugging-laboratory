package com.spicebox.ordering.exception;

public class IllegalStatusChangeException extends RuntimeException {

    public IllegalStatusChangeException(String from, String to) {
        super("An order that is " + from + " cannot move to " + to);
    }
}
