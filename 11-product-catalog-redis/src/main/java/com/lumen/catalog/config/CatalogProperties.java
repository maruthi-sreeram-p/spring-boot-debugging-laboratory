package com.lumen.catalog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lumen")
@Getter
@Setter
public class CatalogProperties {

    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class Cache {
        private String keyPrefix = "catalog:";
        private int productTtlMinutes = 30;
        private int listingTtlMinutes = 15;
        private int searchTtlMinutes = 5;
    }
}
