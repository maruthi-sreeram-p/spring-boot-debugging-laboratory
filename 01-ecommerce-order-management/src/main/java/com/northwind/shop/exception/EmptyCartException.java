package com.northwind.shop.exception;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("Cart is empty");
    }
}
