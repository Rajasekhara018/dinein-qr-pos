package com.railcab.nms.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class NotificationPhoneTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean phoneAccepted(String phone) {
        NotificationRequest r = new NotificationRequest();
        r.setToPhone(phone);
        return validator.validateProperty(r, "toPhone").isEmpty();
    }

    @Test
    void acceptsBareIndianE164AndOtherCountries() {
        assertTrue(phoneAccepted("9876543210"));
        assertTrue(phoneAccepted("+919876543210"));
        assertTrue(phoneAccepted("+12025550123"));
        assertTrue(phoneAccepted("+971501234567"));
        assertTrue(phoneAccepted(null));
    }

    @Test
    void rejectsGarbage() {
        assertFalse(phoneAccepted("not-a-phone"));
        assertFalse(phoneAccepted("123"));
    }

    @Test
    void uamPhoneBecomesE164FromItsOwnCountryCode() {
        assertEquals("+919876543210", new UamUserSummary.PhoneNumber("+91", "9876543210").e164());
        assertEquals("+919876543210", new UamUserSummary.PhoneNumber("91", "9876543210").e164());
        assertEquals("+12025550123", new UamUserSummary.PhoneNumber("+1", "2025550123").e164());
        assertEquals("+971501234567", new UamUserSummary.PhoneNumber("971", "501234567").e164());
        assertEquals("+919876543210", new UamUserSummary.PhoneNumber(null, "9876543210").e164());
        assertEquals("", new UamUserSummary.PhoneNumber("+91", null).e164());
    }
}
