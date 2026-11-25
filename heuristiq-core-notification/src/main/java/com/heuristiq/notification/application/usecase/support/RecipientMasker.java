package com.heuristiq.notification.application.usecase.support;

/**
 * Always-on masking for recipients that leave NMS (attempt records, audit events). Unlike the
 * configurable masking of the legacy log row, this one cannot be switched off: an attempt record
 * and an audit event never carry a full phone number, email address or push token.
 */
public final class RecipientMasker {

    private static final int VISIBLE_TAIL = 4;
    private static final String MASK = "***";

    private RecipientMasker() {
    }

    /**
     * Email: first character and the domain ({@code j***@example.com}). Anything else: the last four
     * characters ({@code ******3210}); values of four characters or fewer are fully masked.
     */
    public static String mask(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return null;
        }
        String value = recipient.trim();
        int at = value.indexOf('@');
        if (at > 0) {
            return value.charAt(0) + MASK + value.substring(at);
        }
        if (value.length() <= VISIBLE_TAIL) {
            return MASK;
        }
        return "*".repeat(Math.min(value.length() - VISIBLE_TAIL, 12)) + value.substring(value.length() - VISIBLE_TAIL);
    }
}
