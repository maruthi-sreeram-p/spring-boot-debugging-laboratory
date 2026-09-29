package com.riverstone.orderevents.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "riverstone")
@Getter
@Setter
public class RiverstoneProperties {

    private Topics topics = new Topics();
    private Groups groups = new Groups();

    @Getter
    @Setter
    public static class Topics {
        private String ordersCreated = "orders.created";
        private String inventoryEvents = "inventory.events";
        private int partitions = 3;
    }

    @Getter
    @Setter
    public static class Groups {
        private String inventory = "inventory-service";
        private String downstream = "order-pipeline";
    }
}
