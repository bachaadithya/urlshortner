package com.urlshortener.service.exception;

public class AliasTakenException extends RuntimeException {

    public AliasTakenException(String alias) {
        super("Custom alias already taken: " + alias);
    }
}
