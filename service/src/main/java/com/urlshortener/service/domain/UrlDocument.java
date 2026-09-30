package com.urlshortener.service.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "urls")
public class UrlDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String shortCode;

    private String originalUrl;
    private boolean custom;
    private Instant createdAt;
    private Instant expiresAt;
    private long clickCount;
    private String createdBy;

    public UrlDocument() {
    }

    public UrlDocument(String shortCode, String originalUrl, boolean custom, String createdBy) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.custom = custom;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.clickCount = 0L;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public boolean isCustom() {
        return custom;
    }

    public void setCustom(boolean custom) {
        this.custom = custom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
