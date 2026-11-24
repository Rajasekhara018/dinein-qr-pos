package com.heuristiq.uam.users.repository;

import com.heuristiq.uam.enums.UamEnums.UserStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Backend-neutral filter for {@link UamUserRepository#search} / {@link UamUserRepository#count}.
 * Every field is optional (null / empty = no constraint); all present constraints are ANDed.
 * Each adapter (Mongo Criteria / JPA Specification) translates this into a database-side query,
 * so filtering, counting and paging never happen in memory.
 *
 * @param roleCode      user must hold this role code (a UserType is represented as its role,
 *                      e.g. "CUSTOMER" -- see UserTypeResolver / UamUserService#normalizeCodes)
 * @param status        exact account status
 * @param searchTerms   each term must case-insensitively appear (substring) in at least one of
 *                      userName, email, phone number, displayName, first/middle/last name
 * @param tenantScope   when non-null, only users of this tenant plus global (null-tenant) users
 * @param createdFrom   audit.createdAt &gt;= this (inclusive)
 * @param createdBefore audit.createdAt &lt; this (exclusive)
 */
public record UamUserSearchCriteria(
        String roleCode,
        UserStatus status,
        List<String> searchTerms,
        String tenantScope,
        LocalDateTime createdFrom,
        LocalDateTime createdBefore
) {
    public UamUserSearchCriteria {
        searchTerms = searchTerms == null ? List.of() : List.copyOf(searchTerms);
    }

    public static UamUserSearchCriteria of(String roleCode, UserStatus status, String search, String tenantScope) {
        return new UamUserSearchCriteria(roleCode, status, tokenize(search), tenantScope, null, null);
    }

    public UamUserSearchCriteria withStatus(UserStatus newStatus) {
        return new UamUserSearchCriteria(roleCode, newStatus, searchTerms, tenantScope, createdFrom, createdBefore);
    }

    public UamUserSearchCriteria withCreatedRange(LocalDateTime from, LocalDateTime before) {
        return new UamUserSearchCriteria(roleCode, status, searchTerms, tenantScope, from, before);
    }

    /** Splits free text on whitespace so "jane doe" matches a user whose first and last name
     *  are stored in separate fields (both backends, consistently). */
    public static List<String> tokenize(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        return Arrays.stream(search.trim().split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
    }
}
