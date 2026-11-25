package com.heuristiq.identity.orgstructure.domain;

/**
 * How much of the organization a caller may see. A SUPER_ADMIN is always {@link #ORG_WIDE}, whatever
 * position they hold; other callers' modes come from their position once the data-scope layer lands.
 */
public enum ScopeMode {
    SELF,
    DIRECT_REPORTS,
    SUBTREE,
    PEER_UNITS,
    ORG_WIDE
}
