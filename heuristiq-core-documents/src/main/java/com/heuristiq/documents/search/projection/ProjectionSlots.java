package com.heuristiq.documents.search.projection;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** Builds slot values the way Rider_SearchAPI's catalogue expects them. Stateless and pure. */
public final class ProjectionSlots {

    private ProjectionSlots() {
    }

    /** Text as stored, or null when absent. */
    public static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Lower-cased text (the catalogue's {@code lower} normalizer), or null. */
    public static String lower(Object value) {
        return value == null ? null : String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    /** Upper-cased text (the catalogue's {@code upper} normalizer), or null. */
    public static String upper(Object value) {
        return value == null ? null : String.valueOf(value).toUpperCase(Locale.ROOT);
    }

    /** ISO-8601 instant, or null. */
    public static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    /** Joins the non-blank parts, lower-cased, for the free-text slot; null when nothing is left. */
    public static String searchText(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!out.isEmpty()) {
                    out.append(ProjectionConstants.QUERY_TEXT_SEPARATOR);
                }
                out.append(part.toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? null : out.toString();
    }

    /** Puts a slot only when it has a value (the assembler ignores absent slots). */
    public static void put(Map<String, Object> fields, String slot, Object value) {
        if (value != null) {
            fields.put(slot, value);
        }
    }
}
