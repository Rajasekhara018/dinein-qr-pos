package com.railcab.nms.adapters.out.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VendorPhoneFormatTest {

    @Test
    void bareIndianNumberGetsIndianCode() {
        assertEquals("+919876543210", VendorPhoneFormat.e164("9876543210"));
        assertEquals("919876543210", VendorPhoneFormat.digits("9876543210"));
    }

    @Test
    void e164NumbersKeepTheirOwnCountry() {
        assertEquals("+919876543210", VendorPhoneFormat.e164("+919876543210"));
        assertEquals("12025550123", VendorPhoneFormat.digits("+1 202 555 0123"));
        assertEquals("971501234567", VendorPhoneFormat.digits("+971501234567"));
        assertEquals("+971501234567", VendorPhoneFormat.e164("+971501234567"));
    }

    @Test
    void garbageIsPassedThrough() {
        assertEquals("abc", VendorPhoneFormat.e164("abc"));
        assertEquals("abc", VendorPhoneFormat.digits("abc"));
    }
}
