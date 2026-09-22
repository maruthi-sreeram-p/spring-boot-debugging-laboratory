package com.meridian.banking.exception;

import java.math.BigDecimal;

public class DailyLimitExceededException extends RuntimeException {

    public DailyLimitExceededException(BigDecimal attemptedTotal, BigDecimal limit) {
        super("Daily transfer limit exceeded: " + attemptedTotal + " requested against a limit of " + limit);
    }
}
