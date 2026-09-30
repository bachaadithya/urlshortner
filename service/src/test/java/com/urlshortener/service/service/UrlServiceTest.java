package com.urlshortener.service.service;

import com.urlshortener.service.bloom.BloomFilterService;
import com.urlshortener.service.cache.UrlCache;
import com.urlshortener.service.config.AppProperties;
import com.urlshortener.service.domain.UrlDocument;
import com.urlshortener.service.dto.CreateUrlRequest;
import com.urlshortener.service.dto.CreateUrlResponse;
import com.urlshortener.service.encoding.ShortCodeGenerator;
import com.urlshortener.service.exception.AliasTakenException;
import com.urlshortener.service.exception.InvalidAliasException;
import com.urlshortener.service.exception.UrlNotFoundException;
import com.urlshortener.service.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;
    @Mock
    private UrlCache urlCache;
    @Mock
    private BloomFilterService bloomFilterService;
    @Mock
    private ClickCountService clickCountService;
    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    private AppProperties appProperties;
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setPublicBaseUrl("https://short.ly");
        appProperties.setShortCodeLength(7);
        appProperties.setMaxGenerationRetries(5);
        appProperties.setReservedAliases(List.of("api", "health"));
        urlService = new UrlService(
                urlRepository, urlCache, bloomFilterService, clickCountService, shortCodeGenerator, appProperties);
    }

    @Test
    void createWithGeneratedCodePersistsThenReturnsResponse() {
        when(shortCodeGenerator.nextCode()).thenReturn("k9Qm2xL");
        when(urlRepository.insert(any(UrlDocument.class))).thenAnswer(invocation -> {
            UrlDocument doc = invocation.getArgument(0);
            doc.setId("id-1");
            return doc;
        });

        CreateUrlRequest request = new CreateUrlRequest();
        request.setUrl("https://example.com/landing");

        CreateUrlResponse response = urlService.create(request, "default");

        assertEquals("k9Qm2xL", response.getShortCode());
        assertEquals("https://short.ly/k9Qm2xL", response.getShortUrl());
        assertEquals("https://example.com/landing", response.getOriginalUrl());
        verify(urlRepository).insert(any(UrlDocument.class));
        verify(urlCache).put("k9Qm2xL", "https://example.com/landing");
        verify(bloomFilterService).add("k9Qm2xL");
        verify(urlCache, never()).nextSequenceId();
    }

    @Test
    void createWithCustomAliasUsesAtomicInsert() {
        when(urlRepository.insert(any(UrlDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateUrlRequest request = new CreateUrlRequest();
        request.setUrl("https://shop.example.com/sale");
        request.setCustomAlias("blackfriday");

        CreateUrlResponse response = urlService.create(request, "default");

        ArgumentCaptor<UrlDocument> captor = ArgumentCaptor.forClass(UrlDocument.class);
        verify(urlRepository).insert(captor.capture());
        assertEquals("blackfriday", captor.getValue().getShortCode());
        assertTrue(captor.getValue().isCustom());
        assertEquals("https://short.ly/blackfriday", response.getShortUrl());
        verify(urlRepository, never()).existsByShortCode(anyString());
        verify(shortCodeGenerator, never()).nextCode();
    }

    @Test
    void concurrentCustomAliasClaimsProduceOneWinnerAndOneConflict() throws Exception {
        AtomicInteger insertCalls = new AtomicInteger();
        when(urlRepository.insert(any(UrlDocument.class))).thenAnswer(invocation -> {
            if (insertCalls.getAndIncrement() == 0) {
                return invocation.getArgument(0);
            }
            throw new DuplicateKeyException("E11000 duplicate key");
        });

        CreateUrlRequest request = new CreateUrlRequest();
        request.setUrl("https://shop.example.com/sale");
        request.setCustomAlias("blackfriday");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> first = pool.submit(() -> {
            start.await();
            try {
                return urlService.create(request, "user-a");
            } catch (RuntimeException ex) {
                return ex;
            }
        });
        Future<Object> second = pool.submit(() -> {
            start.await();
            try {
                return urlService.create(request, "user-b");
            } catch (RuntimeException ex) {
                return ex;
            }
        });
        start.countDown();

        Object r1 = first.get();
        Object r2 = second.get();
        pool.shutdownNow();

        boolean oneSuccess = (r1 instanceof CreateUrlResponse) ^ (r2 instanceof CreateUrlResponse);
        boolean oneConflict = (r1 instanceof AliasTakenException) || (r2 instanceof AliasTakenException);
        assertTrue(oneSuccess);
        assertTrue(oneConflict);
        verify(urlRepository, times(2)).insert(any(UrlDocument.class));
    }

    @Test
    void customAliasDuplicateKeyMapsToAliasTaken() {
        when(urlRepository.insert(any(UrlDocument.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        CreateUrlRequest request = new CreateUrlRequest();
        request.setUrl("https://example.com");
        request.setCustomAlias("promo2026");

        assertThrows(AliasTakenException.class, () -> urlService.create(request, "default"));
        verify(urlCache, never()).put(anyString(), anyString());
        verify(bloomFilterService, never()).add(anyString());
    }

    @Test
    void rejectsReservedCustomAlias() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setUrl("https://example.com");
        request.setCustomAlias("api");

        assertThrows(InvalidAliasException.class, () -> urlService.create(request, "default"));
        verify(urlRepository, never()).insert(any(UrlDocument.class));
    }

    @Test
    void resolveUsesCacheAsideOnHit() {
        when(urlCache.get("promo2026")).thenReturn(Optional.of("https://example.com/promo"));

        String resolved = urlService.resolveOriginalUrl("promo2026");

        assertEquals("https://example.com/promo", resolved);
        verify(urlRepository, never()).findByShortCode(anyString());
        verify(clickCountService).incrementAsync("promo2026");
    }

    @Test
    void resolveLoadsFromDbOnCacheMissAndPopulatesCache() {
        UrlDocument document = new UrlDocument("promo2026", "https://example.com/promo", true, "default");
        document.setCreatedAt(Instant.now());
        when(urlCache.get("promo2026")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode("promo2026")).thenReturn(Optional.of(document));

        String resolved = urlService.resolveOriginalUrl("promo2026");

        assertEquals("https://example.com/promo", resolved);
        verify(urlCache).put("promo2026", "https://example.com/promo");
    }

    @Test
    void resolveThrowsWhenMissing() {
        when(urlCache.get("missing")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> urlService.resolveOriginalUrl("missing"));
    }

    @Test
    void metadataReturnsDocumentFields() {
        UrlDocument document = new UrlDocument("abc1234", "https://example.com", false, "default");
        document.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        document.setClickCount(42);
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(document));

        var meta = urlService.getMetadata("abc1234");

        assertEquals("abc1234", meta.getShortCode());
        assertEquals("https://short.ly/abc1234", meta.getShortUrl());
        assertEquals(42, meta.getClickCount());
    }
}
