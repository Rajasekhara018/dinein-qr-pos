package com.heuristiq.uam.role;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.security.TenantGuard;

/**
 * The platform administrator role belongs to the platform, not to any organization: people working inside an organization
 * must not see it (or pick it) even though it is shared. Only a platform administrator sees it.
 */
public final class RoleVisibility {

    /** Code of the platform-wide administrator role. */

    private RoleVisibility() {
    }

    /** True when the signed-in caller must not see this role. */
    public static boolean hiddenFromCaller(UamRole role) {
        return role != null && UamCommonConstants.SUPER_ADMIN_ROLE.equalsIgnoreCase(role.getCode()) && TenantGuard.isSignedInNonSuperAdmin();
    }
}
