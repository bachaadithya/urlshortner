package com.urlshortener.service.exception;

public class InvalidAliasException extends RuntimeException {

    public InvalidAliasException(String message) {
        super(message);
    }
}
