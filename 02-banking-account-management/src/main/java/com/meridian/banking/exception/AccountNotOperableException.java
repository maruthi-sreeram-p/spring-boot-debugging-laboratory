package com.meridian.banking.exception;

public class AccountNotOperableException extends RuntimeException {

    public AccountNotOperableException(String accountNumber, String status) {
        super("Account " + accountNumber + " is " + status + " and cannot be used for this operation");
    }
}
