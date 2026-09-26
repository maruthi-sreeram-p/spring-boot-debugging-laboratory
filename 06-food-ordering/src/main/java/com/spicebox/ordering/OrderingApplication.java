package com.spicebox.ordering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderingApplication.class, args);
    }
}
