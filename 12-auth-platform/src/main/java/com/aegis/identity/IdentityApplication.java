package com.aegis.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.aegis.identity.config.IdentityProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
