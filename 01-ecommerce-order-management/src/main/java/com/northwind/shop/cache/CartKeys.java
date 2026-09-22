package com.northwind.shop.cache;

import com.northwind.shop.config.ShopProperties;
import org.springframework.stereotype.Component;

/**
 * Central place for the Redis key layout used by the shopping cart.
 * The prefix is configurable so that several environments can share one Redis instance.
 */
@Component
public class CartKeys {

    private final ShopProperties properties;

    public CartKeys(ShopProperties properties) {
        this.properties = properties;
    }

    public String cartOf(Long customerId) {
        return properties.getCart().getKeyPrefix() + customerId;
    }
}
