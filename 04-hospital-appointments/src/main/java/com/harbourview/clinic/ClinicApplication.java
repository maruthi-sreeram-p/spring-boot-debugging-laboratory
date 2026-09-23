package com.harbourview.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ClinicApplication {

    static {
        // The clinic runs in one place and books in local clinic time. Pinning the JVM
        // default keeps appointment times consistent wherever a node happens to run.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ClinicApplication.class, args);
    }
}
