package com.heuristiq.notification.application.security;

import java.security.Principal;

/**
 * The verified caller of a request: who it is, which organization the session belongs to (the {@code tenantId}
 * claim of the access token) and whether it is a backend service rather than a person.
 *
 * @param userId          subject of the token, or {@code internal-service} for the internal API key
 * @param tenantId        organization of the session; null when the caller has none
 * @param internalService true for the internal API key and for service tokens
 */
public record NmsPrincipal(String userId, String tenantId, boolean internalService) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
