package com.athenaeum.lending.exception;

public class NoCopyAvailableException extends RuntimeException {

    public NoCopyAvailableException(Long bookId) {
        super("No copy of book " + bookId + " is on the shelf");
    }
}
