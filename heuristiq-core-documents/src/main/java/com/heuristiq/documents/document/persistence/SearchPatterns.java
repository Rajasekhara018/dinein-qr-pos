package com.railcab.dms.document.persistence;

import java.util.Locale;

/**
 * Turns user-typed search text into literal patterns for the two stores, so text can never act as a regex or a SQL
 * LIKE wildcard. Pure and stateless.
 */
public final class SearchPatterns {

    /** Escape character used with SQL {@code LIKE ... ESCAPE}. */
    public static final char LIKE_ESCAPE = '\\';

    private SearchPatterns() {
    }

    /** Every character that is not a letter or digit gets a leading backslash, so the result matches {@code text} literally. */
    public static String escapeRegex(String text) {
        StringBuilder out = new StringBuilder(text.length() * 2);
        for (char c : text.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c < 128) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Escapes {@code %}, {@code _} and the escape character itself for use with {@link #LIKE_ESCAPE}. */
    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code %text%} with the text escaped, lower-cased for a case-insensitive match against {@code lower(column)}. */
    public static String likeContains(String text) {
        return "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
    }

    /** {@code text%} with the text escaped, lower-cased. */
    public static String likePrefix(String text) {
        return escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
    }
}
