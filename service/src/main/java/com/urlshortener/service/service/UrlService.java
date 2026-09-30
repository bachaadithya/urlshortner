package com.urlshortener.service.service;

import com.urlshortener.service.bloom.BloomFilterService;
import com.urlshortener.service.cache.UrlCache;
import com.urlshortener.service.config.AppProperties;
import com.urlshortener.service.domain.UrlDocument;
import com.urlshortener.service.dto.CreateUrlRequest;
import com.urlshortener.service.dto.CreateUrlResponse;
import com.urlshortener.service.dto.UrlMetadataResponse;
import com.urlshortener.service.encoding.Base62Encoder;
import com.urlshortener.service.exception.AliasTakenException;
import com.urlshortener.service.exception.InvalidAliasException;
import com.urlshortener.service.exception.ShortCodeGenerationException;
import com.urlshortener.service.exception.UrlNotFoundException;
import com.urlshortener.service.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private final UrlRepository urlRepository;
    private final UrlCache urlCache;
    private final BloomFilterService bloomFilterService;
    private final ClickCountService clickCountService;
    private final AppProperties appProperties;

    public UrlService(
            UrlRepository urlRepository,
            UrlCache urlCache,
            BloomFilterService bloomFilterService,
            ClickCountService clickCountService,
            AppProperties appProperties) {
        this.urlRepository = urlRepository;
        this.urlCache = urlCache;
        this.bloomFilterService = bloomFilterService;
        this.clickCountService = clickCountService;
        this.appProperties = appProperties;
    }

    /**
     * Creates a shortened URL. Custom aliases use a single atomic insert against a unique index —
     * never check-then-insert — so simultaneous claims of the same alias yield one 201 and one 409.
     * The HTTP response is built only after the document is persisted.
     */
    public CreateUrlResponse create(CreateUrlRequest request, String apiKeyId) {
        String originalUrl = request.getUrl().trim();
        String customAlias = normalizeAlias(request.getCustomAlias());

        UrlDocument saved;
        if (customAlias != null) {
            validateCustomAlias(customAlias);
            saved = insertCustomAlias(customAlias, originalUrl, apiKeyId);
        } else {
            saved = insertGeneratedCode(originalUrl, apiKeyId);
        }

        urlCache.put(saved.getShortCode(), saved.getOriginalUrl());
        bloomFilterService.add(saved.getShortCode());

        return toCreateResponse(saved);
    }

    /**
     * Cache-aside redirect lookup: Redis first, then MongoDB, then populate cache.
     */
    public String resolveOriginalUrl(String shortCode) {
        Optional<String> cached = urlCache.get(shortCode);
        if (cached.isPresent()) {
            clickCountService.incrementAsync(shortCode);
            return cached.get();
        }

        UrlDocument document = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (document.getExpiresAt() != null && document.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlNotFoundException(shortCode);
        }

        urlCache.put(document.getShortCode(), document.getOriginalUrl());
        clickCountService.incrementAsync(shortCode);
        return document.getOriginalUrl();
    }

    public UrlMetadataResponse getMetadata(String shortCode) {
        UrlDocument document = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return toMetadataResponse(document);
    }

    /**
     * Atomic custom-alias claim. Relies on MongoDB unique index on shortCode.
     * Two concurrent inserts for the same alias: one succeeds, the other gets DuplicateKeyException → 409.
     */
    UrlDocument insertCustomAlias(String alias, String originalUrl, String apiKeyId) {
        UrlDocument document = new UrlDocument(alias, originalUrl, true, apiKeyId);
        try {
            return urlRepository.insert(document);
        } catch (DuplicateKeyException ex) {
            throw new AliasTakenException(alias);
        }
    }

    /**
     * Generates Base62 codes from a Redis sequence and inserts atomically.
     * Retries only on unexpected duplicate of a generated code.
     */
    UrlDocument insertGeneratedCode(String originalUrl, String apiKeyId) {
        int maxRetries = appProperties.getMaxGenerationRetries();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long seq = urlCache.nextSequenceId();
            String shortCode = Base62Encoder.encodePadded(seq, appProperties.getShortCodeLength());
            if (isReserved(shortCode)) {
                continue;
            }
            UrlDocument document = new UrlDocument(shortCode, originalUrl, false, apiKeyId);
            try {
                return urlRepository.insert(document);
            } catch (DuplicateKeyException ex) {
                log.warn("Generated short code collided (attempt {}/{}): {}", attempt, maxRetries, shortCode);
            }
        }
        throw new ShortCodeGenerationException("Unable to allocate a unique short code after " + maxRetries + " attempts");
    }

    private void validateCustomAlias(String alias) {
        if (!alias.matches("^[A-Za-z0-9_-]+$")) {
            throw new InvalidAliasException("customAlias contains invalid characters");
        }
        if (alias.length() < 3 || alias.length() > 32) {
            throw new InvalidAliasException("customAlias must be between 3 and 32 characters");
        }
        if (isReserved(alias)) {
            throw new InvalidAliasException("customAlias is reserved: " + alias);
        }
    }

    private boolean isReserved(String alias) {
        Set<String> reserved = appProperties.reservedAliasSet();
        return reserved.contains(alias.toLowerCase(Locale.ROOT));
    }

    private String normalizeAlias(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            return null;
        }
        return customAlias.trim();
    }

    private CreateUrlResponse toCreateResponse(UrlDocument document) {
        return new CreateUrlResponse(
                document.getShortCode(),
                buildShortUrl(document.getShortCode()),
                document.getOriginalUrl(),
                document.isCustom(),
                document.getCreatedAt()
        );
    }

    private UrlMetadataResponse toMetadataResponse(UrlDocument document) {
        return new UrlMetadataResponse(
                document.getShortCode(),
                buildShortUrl(document.getShortCode()),
                document.getOriginalUrl(),
                document.isCustom(),
                document.getCreatedAt(),
                document.getExpiresAt(),
                document.getClickCount()
        );
    }

    private String buildShortUrl(String shortCode) {
        String base = appProperties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + shortCode;
    }
}
