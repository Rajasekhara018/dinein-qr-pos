package com.heuristiq.identity.orgstructure.domain;

import java.util.List;
import java.util.Set;

/**
 * Port to the platform's user accounts, so the organization structure can create logins without knowing how users
 * are stored. Implemented on top of the user and role features' own use cases.
 */
public interface LoginAccounts {

    /** Every role code the organization may use (the shared ones and its own), upper-cased. */
    Set<String> roleCodes(String tenantId);

    /** True when a user already has this email (as email or username). */
    boolean emailInUse(String email);

    /**
     * Creates a staff login that signs in with an emailed one-time code: the account gets a random password that
     * nobody is ever shown, and must be changed before it could be used.
     *
     * @param tenantId the organization the new login belongs to: it is added as a member there
     * @return the new user's id
     * @throws OrgStructureException when the account cannot be created (for example the email is taken)
     */
    String createLogin(String tenantId, String email, String fullName, String phone, List<String> roles);
}
