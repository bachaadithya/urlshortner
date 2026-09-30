package com.urlshortener.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String publicBaseUrl = "http://localhost:8080";
    private String apiKey = "dev-api-key-change-me";
    private int shortCodeLength = 7;
    private int maxGenerationRetries = 5;
    private List<String> reservedAliases = new ArrayList<>(List.of(
            "api", "actuator", "health", "metrics", "favicon.ico", "robots.txt"
    ));

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getShortCodeLength() {
        return shortCodeLength;
    }

    public void setShortCodeLength(int shortCodeLength) {
        this.shortCodeLength = shortCodeLength;
    }

    public int getMaxGenerationRetries() {
        return maxGenerationRetries;
    }

    public void setMaxGenerationRetries(int maxGenerationRetries) {
        this.maxGenerationRetries = maxGenerationRetries;
    }

    public List<String> getReservedAliases() {
        return reservedAliases;
    }

    public void setReservedAliases(List<String> reservedAliases) {
        this.reservedAliases = reservedAliases;
    }

    public Set<String> reservedAliasSet() {
        Set<String> set = new HashSet<>();
        for (String alias : reservedAliases) {
            set.add(alias.toLowerCase());
        }
        return set;
    }
}
