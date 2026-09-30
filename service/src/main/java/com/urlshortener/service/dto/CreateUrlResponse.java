package com.urlshortener.service.dto;

import java.time.Instant;

public class CreateUrlResponse {

    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private boolean custom;
    private Instant createdAt;

    public CreateUrlResponse() {
    }

    public CreateUrlResponse(String shortCode, String shortUrl, String originalUrl, boolean custom, Instant createdAt) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
        this.custom = custom;
        this.createdAt = createdAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
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
}
