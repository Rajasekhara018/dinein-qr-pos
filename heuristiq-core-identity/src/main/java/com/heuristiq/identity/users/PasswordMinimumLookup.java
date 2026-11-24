package com.heuristiq.uam.users;

/** Port: the shortest password an administrator may set for an account of an organization. */
public interface PasswordMinimumLookup {

    /** @param tenantId the organization of the account, or null for none; the answer is never below the platform default */
    int minimumLength(String tenantId);
}
