package com.urlshortener.service.encoding;

import com.urlshortener.service.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomBase62ShortCodeGeneratorTest {

    @Test
    void generatesFixedLengthBase62Codes() {
        AppProperties props = new AppProperties();
        props.setShortCodeLength(7);
        RandomBase62ShortCodeGenerator generator = new RandomBase62ShortCodeGenerator(props);

        String code = generator.nextCode();

        assertEquals(7, code.length());
        assertTrue(Base62Encoder.isValidCode(code));
    }

    @Test
    void successiveCodesAreNotSequential() {
        AppProperties props = new AppProperties();
        props.setShortCodeLength(7);
        RandomBase62ShortCodeGenerator generator = new RandomBase62ShortCodeGenerator(props);

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(generator.nextCode());
        }

        assertTrue(codes.size() > 40, "random generator should produce mostly unique codes");
        assertTrue(codes.stream().noneMatch(c -> c.matches("0{6}\\d")), "should not look like padded counters");
    }
}
