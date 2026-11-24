package com.railcab.nms.domain.model;

/**
 * Who a broadcast targets. Resolved against UAM's UserType (see UamUserClient) -- there is no
 * VENDORS segment because vendor identities were never modeled in UAM (they only ever existed
 * in AdminAPI's own Vendor collection), and no city/last-active-day filtering because UAM carries
 * no such fields. Both are deliberate scope cuts, not oversights -- see the migration plan.
 */
public enum BroadcastSegment {
    ALL,
    USERS,
    DRIVERS,
    ADMINS
}
