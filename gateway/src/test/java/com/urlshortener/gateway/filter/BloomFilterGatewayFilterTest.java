package com.urlshortener.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.gateway.bloom.SharedBloomFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloomFilterGatewayFilterTest {

    @Mock
    private SharedBloomFilter sharedBloomFilter;
    @Mock
    private GatewayFilterChain chain;

    private BloomFilterGatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BloomFilterGatewayFilter(sharedBloomFilter, new ObjectMapper());
    }

    @Test
    void rejectsUnknownShortCodeWithoutForwarding() {
        when(sharedBloomFilter.mightContain("randombot")).thenReturn(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/randombot").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void forwardsWhenBloomSaysMaybePresent() {
        when(sharedBloomFilter.mightContain("promo2026")).thenReturn(true);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/promo2026").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void skipsApiPaths() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/urls/promo2026").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verify(sharedBloomFilter, never()).mightContain(any());
    }
}
