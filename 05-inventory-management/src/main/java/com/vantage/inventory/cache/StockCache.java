package com.vantage.inventory.cache;

import com.vantage.inventory.config.InventoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Stock level reads are the busiest thing this service does — every storefront page, every
 * picking screen and every availability check hits them — so the current numbers are kept
 * in Redis and refreshed whenever the database moves.
 *
 * Values are stored as a compact "onHand:reserved" string; there is no need for a
 * serialiser for two integers.
 */
@Component
public class StockCache {

    private static final Logger log = LoggerFactory.getLogger(StockCache.class);

    private final StringRedisTemplate redisTemplate;
    private final InventoryProperties properties;

    public StockCache(StringRedisTemplate redisTemplate, InventoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public String keyFor(Long productId, Long warehouseId) {
        return properties.getCache().getKeyPrefix() + productId;
    }

    public Optional<CachedStock> read(Long productId, Long warehouseId) {
        String raw = redisTemplate.opsForValue().get(keyFor(productId, warehouseId));
        if (raw == null) {
            return Optional.empty();
        }
        int separator = raw.indexOf(':');
        if (separator < 0) {
            log.warn("Discarding malformed stock cache entry for product {}", productId);
            return Optional.empty();
        }
        return Optional.of(new CachedStock(
                Integer.parseInt(raw.substring(0, separator)),
                Integer.parseInt(raw.substring(separator + 1))));
    }

    public void write(Long productId, Long warehouseId, int onHand, int reserved) {
        redisTemplate.opsForValue().set(
                keyFor(productId, warehouseId),
                onHand + ":" + reserved,
                Duration.ofMinutes(properties.getCache().getTtlMinutes()));
        log.debug("Cached stock for product {} in warehouse {}: {} on hand, {} reserved",
                productId, warehouseId, onHand, reserved);
    }

    public void evict(Long productId, Long warehouseId) {
        redisTemplate.delete(keyFor(productId, warehouseId));
        log.debug("Evicted stock cache for product {} in warehouse {}", productId, warehouseId);
    }

    public record CachedStock(int onHand, int reserved) {
    }
}
