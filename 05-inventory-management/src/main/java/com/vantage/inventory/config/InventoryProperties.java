package com.vantage.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "inventory")
@Getter
@Setter
public class InventoryProperties {

    private Cache cache = new Cache();
    private Receiving receiving = new Receiving();

    @Getter
    @Setter
    public static class Cache {
        private String keyPrefix = "inv:stock:";
        private int ttlMinutes = 15;
    }

    @Getter
    @Setter
    public static class Receiving {
        private int overReceiptTolerancePercent = 0;
    }
}
