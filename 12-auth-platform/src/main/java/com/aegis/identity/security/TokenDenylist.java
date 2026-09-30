package com.aegis.identity.security;

import com.aegis.identity.config.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tokens that have been signed by us but must no longer be accepted. Entries expire by
 * themselves once the token they refer to would have expired anyway, so the list stays small.
 */
@Component
public class TokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylist.class);

    private final StringRedisTemplate redisTemplate;
    private final IdentityProperties properties;

    public TokenDenylist(StringRedisTemplate redisTemplate, IdentityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void add(String tokenId, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            log.debug("Token {} had already expired, nothing to deny", tokenId);
            return;
        }
        redisTemplate.opsForValue().set(key(tokenId), "revoked", remaining);
        log.debug("Token {} denied for the next {} seconds", tokenId, remaining.toSeconds());
    }

    public boolean contains(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenId)));
    }

    public Set<String> entries() {
        Set<String> keys = redisTemplate.keys(properties.getDenylist().getKeyPrefix() + "*");
        return keys == null ? Set.of() : new TreeSet<>(keys);
    }

    public long clear() {
        Set<String> keys = redisTemplate.keys(properties.getDenylist().getKeyPrefix() + "*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long removed = redisTemplate.delete(keys);
        return removed == null ? 0 : removed;
    }

    private String key(String tokenId) {
        return properties.getDenylist().getKeyPrefix() + tokenId;
    }
}
