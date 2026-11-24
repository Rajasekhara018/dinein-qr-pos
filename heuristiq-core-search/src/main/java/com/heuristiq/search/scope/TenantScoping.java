package com.heuristiq.search.scope;

/** How a model's records belong to organizations. */
public enum TenantScoping {
    /** Not organization data (system-wide records): every caller sees them. */
    NONE,
    /** Every record belongs to one organization (its {@code ownerKey}): a caller sees only their own organization's; unowned records belong to default. */
    OWN,
    /** As {@link #OWN}, legacy configuration name: unowned records belong only to default. */
    OWN_OR_SHARED
}
