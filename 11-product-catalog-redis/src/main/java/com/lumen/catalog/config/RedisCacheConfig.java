package com.lumen.catalog.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches are named after what they hold, and each one gets a time to live that matches how
 * often the underlying data moves.
 *
 *   products        one product, keyed by id
 *   productsBySku   one product, keyed by SKU
 *   categoryListing everything in one category
 *   productSearch   a page of search results
 *   productBrowse   a filtered list for the browse rail
 *   variants        the variants of one product
 */
@Configuration
public class RedisCacheConfig {

    private final CatalogProperties properties;

    public RedisCacheConfig(CatalogProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = baseConfiguration()
                .entryTtl(Duration.ofMinutes(properties.getCache().getProductTtlMinutes()));

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put("products", defaults);
        perCache.put("productsBySku", defaults);
        perCache.put("variants", defaults);
        perCache.put("categoryListing", baseConfiguration()
                .entryTtl(Duration.ofMinutes(properties.getCache().getListingTtlMinutes())));
        perCache.put("productSearch", baseConfiguration()
                .entryTtl(Duration.ofMinutes(properties.getCache().getSearchTtlMinutes())));
        perCache.put("productBrowse", baseConfiguration()
                .entryTtl(Duration.ofMinutes(properties.getCache().getSearchTtlMinutes())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    private RedisCacheConfiguration baseConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY);

        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(properties.getCache().getKeyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));
    }
}
