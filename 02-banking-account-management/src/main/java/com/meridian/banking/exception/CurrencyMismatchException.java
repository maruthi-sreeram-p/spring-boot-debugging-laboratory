package com.meridian.banking.exception;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String sourceCurrency, String targetCurrency) {
        super("Cannot move money between a " + sourceCurrency + " account and a " + targetCurrency + " account");
    }
}
