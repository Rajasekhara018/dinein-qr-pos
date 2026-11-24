package com.railcab.dms.common.cache;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Defaults and validation of the cache settings. */
class DmsCachePropertiesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static DmsCacheProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("railcab.dms.cache", DmsCacheProperties.class);
    }

    @Test
    void defaultsAreOnWithSixtySecondsAndABoundedSize() {
        DmsCacheProperties p = bind(Map.of());
        assertTrue(p.enabled());
        assertTrue(p.documentTypeRule().enabled());
        assertEquals(60, p.documentTypeRule().ttlSeconds());
        assertEquals(5000, p.documentTypeRule().maxSize());
        assertTrue(VALIDATOR.validate(p).isEmpty());
    }

    @Test
    void switchesAndLimitsAreBound() {
        DmsCacheProperties p = bind(Map.of("railcab.dms.cache.enabled", "false",
                "railcab.dms.cache.document-type-rule.ttl-seconds", "30",
                "railcab.dms.cache.document-type-rule.max-size", "10"));
        assertFalse(p.enabled());
        assertEquals(30, p.documentTypeRule().ttlSeconds());
        assertEquals(10, p.documentTypeRule().maxSize());
    }

    @Test
    void nonPositiveOrHugeLimitsAreRejected() {
        assertFalse(VALIDATOR.validate(bind(Map.of("railcab.dms.cache.document-type-rule.ttl-seconds", "0"))).isEmpty());
        assertFalse(VALIDATOR.validate(bind(Map.of("railcab.dms.cache.document-type-rule.max-size", "0"))).isEmpty());
        assertFalse(VALIDATOR.validate(bind(Map.of("railcab.dms.cache.document-type-rule.ttl-seconds", "999999"))).isEmpty());
    }
}
