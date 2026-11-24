package com.heuristiq.search.scope;

/** A caller without an organization asked for a model that holds per-organization data. */
public class TenantRequiredException extends RuntimeException {

    public TenantRequiredException(String message) {
        super(message);
    }
}
