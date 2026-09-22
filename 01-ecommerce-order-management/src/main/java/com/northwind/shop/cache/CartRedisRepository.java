package com.northwind.shop.cache;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.northwind.shop.config.ShopProperties;

/**
 * Carts are volatile, so they live in Redis rather than in MySQL.
 * Layout: one hash per customer, field = product id, value = quantity.
 */
@Repository
public class CartRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final CartKeys cartKeys;
    private final ShopProperties properties;

    public CartRedisRepository(StringRedisTemplate redisTemplate,
                               CartKeys cartKeys,
                               ShopProperties properties) {
        this.redisTemplate = redisTemplate;
        this.cartKeys = cartKeys;
        this.properties = properties;
    }

    public Map<Long, Integer> findCart(Long customerId) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        Map<String, String> raw = hash.entries(cartKeys.cartOf(customerId));
        Map<Long, Integer> cart = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            cart.put(Long.valueOf(entry.getKey()), Integer.valueOf(entry.getValue()));
        }
        return cart;
    }

    public void increaseQuantity(Long customerId, Long productId, int delta) {
        String key = cartKeys.cartOf(customerId);
        redisTemplate.opsForHash().increment(key, String.valueOf(productId), delta);
        refreshExpiry(key);
    }

    public void setQuantity(Long customerId, Long productId, int quantity) {
        String key = cartKeys.cartOf(customerId);
        redisTemplate.opsForHash().put(key, String.valueOf(productId), String.valueOf(quantity));
        refreshExpiry(key);
    }

    public void removeItem(Long customerId, Long productId) {
        redisTemplate.opsForHash().delete(cartKeys.cartOf(customerId), String.valueOf(productId));
    }

    public void clear(Long customerId) {
        redisTemplate.delete(cartKeys.cartOf(customerId));
    }

    private void refreshExpiry(String key) {
        redisTemplate.expire(key, Duration.ofHours(properties.getCart().getTtlHours()));
    }
}
