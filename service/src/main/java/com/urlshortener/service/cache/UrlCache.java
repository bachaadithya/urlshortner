package com.urlshortener.service.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside store for shortCode → originalUrl lookups.
 */
@Component
public class UrlCache {

    public static final String KEY_PREFIX = "url:";
    public static final String SEQ_KEY = "url:seq";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public UrlCache(
            StringRedisTemplate redis,
            @Value("${app.cache.ttl-seconds:3600}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<String> get(String shortCode) {
        String value = redis.opsForValue().get(KEY_PREFIX + shortCode);
        return Optional.ofNullable(value);
    }

    public void put(String shortCode, String originalUrl) {
        redis.opsForValue().set(KEY_PREFIX + shortCode, originalUrl, ttl);
    }

    public void evict(String shortCode) {
        redis.delete(KEY_PREFIX + shortCode);
    }

    /**
     * Atomically allocates the next sequence id for Base62 encoding.
     */
    public long nextSequenceId() {
        Long id = redis.opsForValue().increment(SEQ_KEY);
        if (id == null) {
            throw new IllegalStateException("Failed to allocate short-code sequence from Redis");
        }
        return id;
    }
}
