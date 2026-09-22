package com.meridian.banking.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountNumber, BigDecimal requested, BigDecimal available) {
        super("Account " + accountNumber + " has insufficient funds: requested " + requested + ", available " + available);
    }
}
