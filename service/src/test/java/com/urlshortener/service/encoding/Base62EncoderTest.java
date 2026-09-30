package com.urlshortener.service.encoding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62EncoderTest {

    @Test
    void encodesZeroAsZero() {
        assertEquals("0", Base62Encoder.encode(0));
    }

    @Test
    void encodesKnownValues() {
        assertEquals("1", Base62Encoder.encode(1));
        assertEquals("A", Base62Encoder.encode(10));
        assertEquals("Z", Base62Encoder.encode(35));
        assertEquals("a", Base62Encoder.encode(36));
        assertEquals("10", Base62Encoder.encode(62));
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(-1));
    }

    @Test
    void validatesAlphabet() {
        assertTrue(Base62Encoder.isValidCode("aB3xY9"));
        assertFalse(Base62Encoder.isValidCode("bad!"));
        assertFalse(Base62Encoder.isValidCode(""));
        assertFalse(Base62Encoder.isValidCode(null));
        assertFalse(Base62Encoder.isValidCode("has-dash"));
    }
}
