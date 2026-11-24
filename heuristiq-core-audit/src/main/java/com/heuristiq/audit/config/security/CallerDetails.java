package com.railcab.audit.config.security;

/**
 * Organization facts about the authenticated caller, attached to the authentication as its details.
 *
 * @param tenantId         the {@code tenantId} claim of the verified token, or null when the session has none
 * @param allOrganizations true only when the gateway marked the caller organization-wide
 *                         ({@code X-Org-Wide}, set from the verified token) and the caller asked to look across
 *                         organizations ({@code X-View-All-Organizations})
 */
public record CallerDetails(String tenantId, boolean allOrganizations) {
}
