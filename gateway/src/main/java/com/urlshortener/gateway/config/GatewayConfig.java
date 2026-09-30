package com.urlshortener.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${app.rate-limit.replenish-rate:50}") int replenishRate,
            @Value("${app.rate-limit.burst-capacity:100}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            if (exchange.getRequest().getRemoteAddress() != null
                    && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }
            return Mono.just("unknown");
        };
    }
}
