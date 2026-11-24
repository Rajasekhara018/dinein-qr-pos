package com.heuristiq.uam.organization.subscription;

/** State of an organization's use of an application. A cancelled subscription is kept so the history stays readable. */
public enum TenantApplicationStatus {
    ACTIVE, SUSPENDED, CANCELLED
}
