package com.heuristiq.uam.helper;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

/**
 * Validates stored timezone names before they are handed to a client. A stored value that is not a zone this
 * runtime knows (typo, legacy free text, offset-only text) must never reach a client, so it is returned as null.
 */
public final class IanaTimeZone {

    private IanaTimeZone() {
    }

    /**
     * @param value a stored timezone name, may be null or blank
     * @return the trimmed region-based IANA id (for example {@code Asia/Kolkata}) when valid, otherwise null
     */
    public static String validOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !ZoneId.getAvailableZoneIds().contains(trimmed)) {
            return null;
        }
        try {
            ZoneId.of(trimmed);
            return trimmed;
        } catch (ZoneRulesException e) {
            return null;
        } catch (DateTimeException e) {
            return null;
        }
    }
}
