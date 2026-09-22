package com.northwind.shop.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName, int requested, int available) {
        super("Not enough stock for " + productName + ": requested " + requested + ", available " + available);
    }
}
