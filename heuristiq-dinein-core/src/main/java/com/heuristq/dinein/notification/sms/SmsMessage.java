package com.heuristq.dinein.notification.sms;

import java.util.Map;

/**
 * @param to        E.164 number, e.g. {@code +919876543210}
 * @param text      rendered text (at most 160 characters) for providers that take free text (Twilio)
 * @param template  event code, for providers that send pre-registered templates (MSG91 / Indian DLT)
 * @param variables template variables ({@code token}, {@code restaurant}, ...)
 */
public record SmsMessage(String to, String text, String template, Map<String, String> variables) {

    /** Normalises a stored phone number to E.164, adding {@code countryCode} to 10-digit local numbers. */
    public static String e164(String phone, String countryCode) {
        String trimmed = phone.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (trimmed.startsWith("+")) {
            return "+" + digits;
        }
        return digits.length() == 10 ? "+" + countryCode + digits : "+" + digits;
    }
}
