package com.urlshortener.service.web;

import com.urlshortener.service.config.AppProperties;
import com.urlshortener.service.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires {@code X-API-Key} for create and metadata endpoints. Redirects stay public.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            return true;
        }
        return "GET".equalsIgnoreCase(request.getMethod()) && !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || !provided.equals(appProperties.getApiKey())) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    "INVALID_API_KEY",
                    "Valid X-API-Key header is required",
                    request.getRequestURI()
            );
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        request.setAttribute(UrlController.API_KEY_ATTR, "default");
        filterChain.doFilter(request, response);
    }
}
