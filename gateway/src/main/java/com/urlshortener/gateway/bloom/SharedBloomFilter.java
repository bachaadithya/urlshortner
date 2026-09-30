package com.urlshortener.gateway.bloom;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads the shared Guava Bloom filter from Redis (written by the URL service)
 * so the gateway can reject never-created short codes without hitting the DB.
 */
@Component
public class SharedBloomFilter {

    public static final String REDIS_KEY = "url:bloom";
    public static final String BLOOM_CHANNEL = "url:bloom:reload";
    public static final String URL_CACHE_PREFIX = "url:";

    private static final Logger log = LoggerFactory.getLogger(SharedBloomFilter.class);

    private final ReactiveStringRedisTemplate redis;
    private final Duration refreshInterval;
    private final AtomicReference<BloomFilter<String>> bloom = new AtomicReference<>();

    public SharedBloomFilter(
            ReactiveStringRedisTemplate redis,
            @Value("${app.bloom.refresh-seconds:5}") long refreshSeconds) {
        this.redis = redis;
        this.refreshInterval = Duration.ofSeconds(Math.max(1, refreshSeconds));
    }

    @PostConstruct
    public void start() {
        reload().subscribe();
        redis.listenToChannel(BLOOM_CHANNEL)
                .doOnNext(msg -> log.debug("Bloom reload signal received"))
                .flatMap(msg -> reload())
                .onErrorContinue((err, obj) -> log.warn("Bloom pub/sub error", err))
                .subscribe();
        Mono.delay(refreshInterval)
                .repeat()
                .flatMap(tick -> reload())
                .onErrorContinue((err, obj) -> log.warn("Bloom refresh error", err))
                .subscribe();
    }

    public boolean mightContain(String shortCode) {
        BloomFilter<String> filter = bloom.get();
        // Fail open until the filter is loaded so cold starts still work.
        if (filter == null) {
            return true;
        }
        return filter.mightContain(shortCode);
    }

    /**
     * Stale in-memory bloom can miss newly created codes. Reload from Redis, re-check bloom,
     * and fall back to the redirect cache key written on create.
     */
    public Mono<Boolean> mightContainFresh(String shortCode) {
        if (mightContain(shortCode)) {
            return Mono.just(true);
        }
        return reload()
                .then(Mono.fromCallable(() -> mightContain(shortCode)))
                .flatMap(present -> {
                    if (Boolean.TRUE.equals(present)) {
                        return Mono.just(true);
                    }
                    return redis.hasKey(URL_CACHE_PREFIX + shortCode).defaultIfEmpty(false);
                });
    }

    public Mono<Void> reload() {
        return redis.opsForValue().get(REDIS_KEY)
                .flatMap(encoded -> {
                    try {
                        byte[] bytes = Base64.getDecoder().decode(encoded);
                        BloomFilter<String> loaded = BloomFilter.readFrom(
                                new ByteArrayInputStream(bytes),
                                Funnels.stringFunnel(StandardCharsets.UTF_8));
                        bloom.set(loaded);
                        return Mono.empty();
                    } catch (IOException | IllegalArgumentException e) {
                        log.warn("Unable to decode bloom filter from Redis", e);
                        return Mono.empty();
                    }
                })
                .then();
    }
}
