package com.heuristiq.uam.organization;

/** The token claim that marks a caller who may look across organizations, and the privilege that grants it. */
public final class OrgWideConstants {

    /** Token claim, present (true) only for an organization-wide caller; the gateway forwards it as {@code X-Org-Wide}. */
    public static final String CLAIM = "orgWide";

    /**
     * The privilege that makes a role organization-wide: being able to list every organization is a platform-level
     * ability that organization admins do not have. A role that holds it is organization-wide.
     */
    public static final String GRANTING_PRIVILEGE = "ORGANIZATION_LIST";

    /** Granted authority an organization-wide caller carries inside UAM (from the {@link #CLAIM}); not a role. */
    public static final String AUTHORITY = "ORG_WIDE";

    private OrgWideConstants() {
    }
}
