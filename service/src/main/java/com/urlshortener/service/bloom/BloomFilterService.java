package com.urlshortener.service.bloom;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Shared Guava Bloom filter persisted in Redis so the API gateway can reject
 * never-created short codes before they hit the service / database (cache penetration protection).
 */
@Service
public class BloomFilterService {

    public static final String REDIS_KEY = "url:bloom";
    public static final String BLOOM_CHANNEL = "url:bloom:reload";

    private static final Logger log = LoggerFactory.getLogger(BloomFilterService.class);

    private final StringRedisTemplate redis;
    private final long expectedInsertions;
    private final double fpp;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile BloomFilter<String> bloomFilter;

    public BloomFilterService(
            StringRedisTemplate redis,
            @Value("${app.bloom.expected-insertions:1000000}") long expectedInsertions,
            @Value("${app.bloom.fpp:0.01}") double fpp) {
        this.redis = redis;
        this.expectedInsertions = expectedInsertions;
        this.fpp = fpp;
    }

    @PostConstruct
    public void init() {
        lock.writeLock().lock();
        try {
            bloomFilter = loadOrCreate();
            persistUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean mightContain(String shortCode) {
        BloomFilter<String> local = bloomFilter;
        return local != null && local.mightContain(shortCode);
    }

    public void add(String shortCode) {
        lock.writeLock().lock();
        try {
            bloomFilter.put(shortCode);
            persistUnlocked();
            redis.convertAndSend(BLOOM_CHANNEL, "reload");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void reloadFromRedis() {
        lock.writeLock().lock();
        try {
            BloomFilter<String> loaded = loadFromRedis();
            if (loaded != null) {
                bloomFilter = loaded;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private BloomFilter<String> loadOrCreate() {
        BloomFilter<String> loaded = loadFromRedis();
        if (loaded != null) {
            return loaded;
        }
        return BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp);
    }

    private BloomFilter<String> loadFromRedis() {
        String encoded = redis.opsForValue().get(REDIS_KEY);
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return BloomFilter.readFrom(new ByteArrayInputStream(bytes), Funnels.stringFunnel(StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to load bloom filter from Redis, creating a new one", e);
            return null;
        }
    }

    private void persistUnlocked() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bloomFilter.writeTo(out);
            String encoded = Base64.getEncoder().encodeToString(out.toByteArray());
            redis.opsForValue().set(REDIS_KEY, encoded);
        } catch (IOException e) {
            log.error("Failed to persist bloom filter to Redis", e);
        }
    }
}
