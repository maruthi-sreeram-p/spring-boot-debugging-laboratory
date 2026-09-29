package com.lumen.catalog.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A window onto Redis, so the cache can be inspected without leaving the application.
 */
@RestController
@RequestMapping("/api/ops/cache")
public class CacheOpsController {

    private final StringRedisTemplate redisTemplate;

    public CacheOpsController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/keys")
    public List<String> keys(@RequestParam(defaultValue = "*") String pattern) {
        Set<String> found = redisTemplate.keys(pattern);
        return found == null ? List.of() : new TreeSet<>(found).stream().toList();
    }

    @GetMapping("/summary")
    public Map<String, Integer> summary() {
        Set<String> found = redisTemplate.keys("*");
        if (found == null) {
            return Map.of();
        }
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (String key : found) {
            String cacheName = key.contains("::") ? key.substring(0, key.indexOf("::")) : key;
            counts.merge(cacheName, 1, Integer::sum);
        }
        return counts;
    }

    @DeleteMapping
    public Map<String, Object> flush() {
        Set<String> found = redisTemplate.keys("*");
        long removed = found == null ? 0 : redisTemplate.delete(found);
        return Map.of("removed", removed);
    }
}
