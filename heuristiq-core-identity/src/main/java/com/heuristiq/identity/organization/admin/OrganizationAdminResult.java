package com.heuristiq.uam.organization.admin;

/**
 * What was set up for an organization's administrator. The one-time password is present only when this call created the
 * account; it is handed to the caller once and never stored in readable form or logged.
 */
public record OrganizationAdminResult(String userId, String email, String roleCode, boolean accountCreated, String temporaryPassword) {

    @Override
    public String toString() {
        return "OrganizationAdminResult[userId=" + userId + ", roleCode=" + roleCode + ", accountCreated=" + accountCreated + "]";
    }
}
