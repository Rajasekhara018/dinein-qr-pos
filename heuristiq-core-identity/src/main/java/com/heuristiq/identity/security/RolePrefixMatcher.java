package com.heuristiq.identity.security;

import java.util.List;
import java.util.Locale;

/**
 * Matches role codes against configured prefixes. A role matches a prefix when it is exactly that code or that code followed by an
 * underscore and a non-empty suffix (case-insensitive), so {@code FRANCHISE_OWNER} covers {@code FRANCHISE_OWNER_MATRIMONY} but not
 * {@code FRANCHISE_OWNERSHIP_AUDITOR}.
 */
public final class RolePrefixMatcher {

    private static final String SEPARATOR = "_";

    private RolePrefixMatcher() {
    }

    /** True when at least one of the roles matches at least one of the prefixes; null or empty inputs never match. */
    public static boolean matchesAny(List<String> roles, List<String> prefixes) {
        if (roles == null || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(role -> matches(role, prefixes));
    }

    /** True when the role is, or is scoped under, one of the prefixes. */
    public static boolean matches(String role, List<String> prefixes) {
        if (role == null || role.isBlank() || prefixes == null) {
            return false;
        }
        String code = role.trim().toUpperCase(Locale.ROOT);
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            String base = prefix.trim().toUpperCase(Locale.ROOT);
            if (code.equals(base) || (code.startsWith(base + SEPARATOR) && code.length() > base.length() + SEPARATOR.length())) {
                return true;
            }
        }
        return false;
    }
}
