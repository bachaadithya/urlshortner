package com.urlshortener.service.encoding;

/**
 * Encodes positive integers to Base62 (0-9A-Za-z) short codes.
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        long n = value;
        while (n > 0) {
            sb.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Pads the encoded value to at least {@code minLength} characters with leading zeros.
     */
    public static String encodePadded(long value, int minLength) {
        String encoded = encode(value);
        if (encoded.length() >= minLength) {
            return encoded;
        }
        return "0".repeat(minLength - encoded.length()) + encoded;
    }

    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
