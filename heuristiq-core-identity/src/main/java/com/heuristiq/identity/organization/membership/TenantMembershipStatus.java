package com.heuristiq.uam.organization.membership;

/** A person's standing in one organization. A removed membership is kept so the history stays readable. */
public enum TenantMembershipStatus {
    ACTIVE, SUSPENDED, REMOVED
}
