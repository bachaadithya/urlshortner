package com.urlshortener.service.encoding;

/**
 * Strategy for allocating auto-generated short codes (not custom aliases).
 */
public interface ShortCodeGenerator {

    /**
     * Returns a candidate short code. Callers must insert atomically and retry on collision.
     */
    String nextCode();
}
