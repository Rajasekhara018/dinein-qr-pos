package com.heuristiq.uam.organization.membership;

import com.heuristiq.uam.common.UamCommonConstants;

/** Fixed values of organization membership at sign-in. */
public final class TenancyConstants {

    /** Header a client sends (with a login or refresh) to name the organization it wants to work in: its code or id. */
    public static final String ORGANIZATION_HEADER = "X-Organization-Code";

    /** Role of a platform administrator, who may open any active organization. */
    public static final String SUPER_ADMIN_ROLE = UamCommonConstants.SUPER_ADMIN_ROLE;

    /** Recorded as the one who created a membership made automatically. */
    public static final String SYSTEM_ACTOR = "system";

    /** Accounts read per page when giving existing accounts a membership in the default organization. */
    public static final int BACKFILL_PAGE_SIZE = 500;

    /** Most accounts looked at when searching an organization's members by text. */
    public static final int MEMBER_SEARCH_LIMIT = 20;

    private TenancyConstants() {
    }
}
