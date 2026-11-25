package com.heuristiq.identity.organization;

/** How much of an organization a person sees (an organization setting). */
public enum DataScopeMode {
    /** Everyone sees the whole organization. */
    ORGANIZATION,
    /** A person sees only their own department and the departments below it; the organization's administrators see all. */
    DEPARTMENT
}
