package com.heuristq.dinein.notification;

/** Masks recipients for logs and {@code notification_log}: full phone numbers, emails and tokens are never stored. */
public final class RecipientMask {

    private RecipientMask() {
    }

    public static String of(NotificationChannel channel, String value) {
        return switch (channel) {
            case EMAIL -> email(value);
            case SMS -> phone(value);
            case PUSH -> token(value);
            case IN_APP -> value;
        };
    }

    /** {@code rajesh@example.com} becomes {@code ra***@example.com}. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /** Keeps only the last four digits: {@code +919876543210} becomes {@code ******3210}. */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() <= 4 ? "****" : "******" + digits.substring(digits.length() - 4);
    }

    /** Device tokens are credentials: keep a short prefix to correlate log lines. */
    public static String token(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.length() <= 12 ? "***" : token.substring(0, 6) + "...";
    }
}
