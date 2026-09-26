package com.heuristq.dinein.shared.util;

import java.util.regex.Pattern;

/** Sanitises free text from guests: strips control characters and angle brackets, trims, caps length. */
public final class Text {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
    private static final Pattern ANGLE = Pattern.compile("[<>]");

    private Text() {
    }

    public static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = ANGLE.matcher(CONTROL.matcher(value).replaceAll("")).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}
