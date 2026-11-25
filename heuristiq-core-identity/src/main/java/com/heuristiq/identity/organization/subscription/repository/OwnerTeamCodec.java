package com.heuristiq.identity.organization.subscription.repository;

import java.util.Arrays;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * Stores the owner team's user ids in one text column (Postgres) and reads them back. Ids are generated identifiers without
 * commas, so a comma is a safe separator; blanks and duplicates are dropped while reading.
 */
public final class OwnerTeamCodec {

    private static final String SEPARATOR = ",";

    private OwnerTeamCodec() {
    }

    /** The ids as one comma separated text, or null when there are none. */
    public static String encode(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        return String.join(SEPARATOR, userIds);
    }

    /** The ids in a stored text (empty list for null or blank). */
    public static List<String> decode(String stored) {
        if (!StringUtils.hasText(stored)) {
            return List.of();
        }
        return Arrays.stream(stored.split(SEPARATOR)).map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }
}
