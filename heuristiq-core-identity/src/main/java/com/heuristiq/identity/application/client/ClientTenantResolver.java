package com.heuristiq.uam.application.client;

/**
 * Decides which organization an API client belongs to. A person working inside an organization can only make clients for
 * that organization (taken from the token); a platform administrator may name any existing organization, or none for a
 * platform-wide client.
 */
public interface ClientTenantResolver {

    /**
     * @param requestedTenantId the organization named in the request (an id or a code), possibly blank
     * @return the id of the organization the client belongs to, or {@code null} for a platform-wide client
     * @throws com.heuristiq.uam.common.UamException when the organization does not exist, or is another organization than the caller's
     */
    String resolve(String requestedTenantId);
}
