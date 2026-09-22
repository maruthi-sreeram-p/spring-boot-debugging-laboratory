package com.meridian.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BankingApplication {

    static {
        // Every timestamp in the ledger is written, read and compared in UTC, whatever
        // region a node happens to run in. Set before any connection pool starts up.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
