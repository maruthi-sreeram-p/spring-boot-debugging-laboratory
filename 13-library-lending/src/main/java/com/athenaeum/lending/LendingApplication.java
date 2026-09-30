package com.athenaeum.lending;

import com.athenaeum.lending.config.CirculationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(CirculationProperties.class)
@EnableScheduling
public class LendingApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(LendingApplication.class, args);
    }
}
