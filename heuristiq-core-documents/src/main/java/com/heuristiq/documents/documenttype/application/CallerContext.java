package com.heuristiq.documents.documenttype.application;

/**
 * Who is asking, as the web layer resolved it from the verified session.
 *
 * @param userId      the maker's user id (from the gateway headers)
 * @param tenantId    the caller's organization (the {@code tenantId} claim of the token), never from a request body
 * @param bearerToken the caller's own access token without the "Bearer " prefix, forwarded to the approval service so
 *                    the caller is recorded as the maker
 */
public record CallerContext(String userId, String tenantId, String bearerToken) {
}
