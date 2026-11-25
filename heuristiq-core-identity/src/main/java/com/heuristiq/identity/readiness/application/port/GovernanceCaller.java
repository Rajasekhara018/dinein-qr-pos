package com.heuristiq.identity.readiness.application.port;

/** Who is calling the current request, always from the token. */
public interface GovernanceCaller {

    /** True when the caller holds the SUPER_ADMIN role. */
    boolean isSuperAdmin();

    /** The caller's account id, or null when there is no signed-in person. */
    String userId();
}
