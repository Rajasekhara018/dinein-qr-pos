package com.heuristiq.gateway.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @Test
    void validKey_inSingleKeyConfig_returnsTrue() {
        assertTrue(SecurityUtils.validateInternalTokenKey("secret-key", "secret-key"));
    }

    @Test
    void validKey_inMultipleKeyConfig_returnsTrue() {
        assertTrue(SecurityUtils.validateInternalTokenKey("key-b", "key-a,key-b,key-c"));
    }

    @Test
    void validKey_withWhitespace_returnsTrue() {
        assertTrue(SecurityUtils.validateInternalTokenKey("key-a", "key-a , key-b"));
    }

    @Test
    void wrongKey_returnsFalse() {
        assertFalse(SecurityUtils.validateInternalTokenKey("wrong-key", "key-a,key-b"));
    }

    @Test
    void nullProvidedKey_returnsFalse() {
        assertFalse(SecurityUtils.validateInternalTokenKey(null, "key-a"));
    }

    @Test
    void emptyProvidedKey_returnsFalse() {
        assertFalse(SecurityUtils.validateInternalTokenKey("", "key-a"));
    }

    @Test
    void nullConfiguredKeys_returnsFalse() {
        assertFalse(SecurityUtils.validateInternalTokenKey("key-a", null));
    }

    @Test
    void emptyConfiguredKeys_returnsFalse() {
        assertFalse(SecurityUtils.validateInternalTokenKey("key-a", ""));
    }

    @Test
    void prefixMatch_doesNotGrantAccess() {
        // "key-a-extra" must not match against configured "key-a"
        assertFalse(SecurityUtils.validateInternalTokenKey("key-a-extra", "key-a,key-b"));
    }
}
