package com.railcab.audit.config.security;

import static com.railcab.audit.config.security.SecurityConstants.*;
import java.util.List;

import io.jsonwebtoken.Claims;

/** Reads the role claims of a token ({@code roles} list or single {@code role}). */
final class TokenRoles {

    /** Role carried by the tokens services mint for each other (see Rider_WorkflowService). */

    private TokenRoles() {
    }

    static List<String> of(Claims claims) {
        Object roles = claims.get(ROLES_CLAIM);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        Object role = claims.get(ROLE_CLAIM);
        return role == null ? List.of() : List.of(String.valueOf(role));
    }

    static boolean isService(Claims claims) {
        return of(claims).stream().anyMatch(r -> SERVICE_ROLE.equals(r) || (ROLE_PREFIX + SERVICE_ROLE).equals(r));
    }
}
