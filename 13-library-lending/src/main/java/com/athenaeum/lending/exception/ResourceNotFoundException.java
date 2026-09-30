package com.athenaeum.lending.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String type, Object id) {
        super(type + " not found: " + id);
    }
}
