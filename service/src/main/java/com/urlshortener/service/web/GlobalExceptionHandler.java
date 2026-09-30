package com.urlshortener.service.web;

import com.urlshortener.service.dto.ErrorResponse;
import com.urlshortener.service.exception.AliasTakenException;
import com.urlshortener.service.exception.InvalidAliasException;
import com.urlshortener.service.exception.ShortCodeGenerationException;
import com.urlshortener.service.exception.UrlNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AliasTakenException.class)
    public ResponseEntity<ErrorResponse> handleAliasTaken(AliasTakenException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "ALIAS_TAKEN", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(UrlNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "URL_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidAliasException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAlias(InvalidAliasException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ALIAS", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ShortCodeGenerationException.class)
    public ResponseEntity<ErrorResponse> handleGeneration(ShortCodeGenerationException ex, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SHORT_CODE_UNAVAILABLE", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request.getRequestURI());
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, String path) {
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), code, message, path);
        return ResponseEntity.status(status).body(body);
    }
}
