package com.urlshortener.service.web;

import com.urlshortener.service.dto.CreateUrlRequest;
import com.urlshortener.service.dto.CreateUrlResponse;
import com.urlshortener.service.dto.UrlMetadataResponse;
import com.urlshortener.service.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping
public class UrlController {

    public static final String API_KEY_ATTR = "apiKeyId";

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> create(
            @Valid @RequestBody CreateUrlRequest request,
            HttpServletRequest httpRequest) {
        String apiKeyId = (String) httpRequest.getAttribute(API_KEY_ATTR);
        CreateUrlResponse response = urlService.create(request, apiKeyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/urls/{shortCode}")
    public UrlMetadataResponse metadata(@PathVariable String shortCode) {
        return urlService.getMetadata(shortCode);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlService.resolveOriginalUrl(shortCode);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
