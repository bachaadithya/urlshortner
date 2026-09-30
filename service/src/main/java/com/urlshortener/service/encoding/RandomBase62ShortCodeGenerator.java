package com.urlshortener.service.encoding;

import com.urlshortener.service.config.AppProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Cryptographically random Base62 short codes — not sequential, so codes are not guessable.
 * Example output: {@code k9Qm2xL} instead of {@code 0000001}.
 */
@Component
public class RandomBase62ShortCodeGenerator implements ShortCodeGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();
    private final int codeLength;

    public RandomBase62ShortCodeGenerator(AppProperties appProperties) {
        this.codeLength = Math.max(4, appProperties.getShortCodeLength());
    }

    @Override
    public String nextCode() {
        char[] chars = new char[codeLength];
        for (int i = 0; i < codeLength; i++) {
            chars[i] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }
}
