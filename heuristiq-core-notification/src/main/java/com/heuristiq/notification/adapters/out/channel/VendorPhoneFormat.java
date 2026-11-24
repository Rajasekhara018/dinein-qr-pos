package com.heuristiq.notification.adapters.out.channel;

import com.heuristiq.phone.PhoneNumbers;

/**
 * Writes a recipient's phone number the way each messaging vendor wants it, always derived from the number itself (its own
 * country code), never from a fixed country. A number that cannot be parsed is passed through unchanged so the vendor
 * reports the failure and it is recorded against the attempt.
 */
public final class VendorPhoneFormat {

    private VendorPhoneFormat() {
    }

    /** E.164 with the leading plus (Twilio). A bare number is read in the default region. */
    public static String e164(String raw) {
        return PhoneNumbers.toE164(raw).orElse(raw);
    }

    /** Country code and number as digits only, no plus (Digimile, Meta WhatsApp Cloud API). */
    public static String digits(String raw) {
        return PhoneNumbers.toE164(raw).map(e -> e.substring(1)).orElse(raw);
    }
}
