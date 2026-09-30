package com.urlshortener.gateway.filter;

import com.urlshortener.gateway.bloom.SharedBloomFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects GET /{shortCode} requests that are known-absent (bloom + cache check),
 * preventing cache-penetration attacks from reaching MongoDB.
 */
@Component
public class BloomFilterGatewayFilter implements GlobalFilter, Ordered {

    private static final Pattern SHORT_CODE_PATH = Pattern.compile("^/[A-Za-z0-9_-]{3,32}$");
    private static final Set<String> SKIP_PREFIXES = Set.of("/api", "/actuator");

    private final SharedBloomFilter sharedBloomFilter;
    private final ObjectMapper objectMapper;

    public BloomFilterGatewayFilter(SharedBloomFilter sharedBloomFilter, ObjectMapper objectMapper) {
        this.sharedBloomFilter = sharedBloomFilter;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.GET) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (shouldSkip(path) || !SHORT_CODE_PATH.matcher(path).matches()) {
            return chain.filter(exchange);
        }

        String shortCode = path.substring(1);
        return sharedBloomFilter.mightContainFresh(shortCode)
                .flatMap(allowed -> {
                    if (!Boolean.TRUE.equals(allowed)) {
                        return writeNotFound(exchange, shortCode);
                    }
                    return chain.filter(exchange);
                });
    }

    private boolean shouldSkip(String path) {
        for (String prefix : SKIP_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> writeNotFound(ServerWebExchange exchange, String shortCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 404);
        body.put("error", "Not Found");
        body.put("code", "URL_NOT_FOUND");
        body.put("message", "Short URL not found: " + shortCode);
        body.put("path", exchange.getRequest().getURI().getPath());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"message\":\"Short URL not found\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
