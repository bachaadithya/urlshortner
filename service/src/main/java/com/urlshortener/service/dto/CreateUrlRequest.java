package com.urlshortener.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateUrlRequest {

    @NotBlank(message = "url is required")
    @Pattern(
            regexp = "^https?://[\\w.-]+(:\\d+)?(/\\S*)?$",
            message = "url must be a valid http or https URL"
    )
    @Size(max = 2048, message = "url must be at most 2048 characters")
    private String url;

    @Size(min = 3, max = 32, message = "customAlias must be between 3 and 32 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9_-]+$",
            message = "customAlias may only contain letters, digits, underscore, and hyphen"
    )
    private String customAlias;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }
}
