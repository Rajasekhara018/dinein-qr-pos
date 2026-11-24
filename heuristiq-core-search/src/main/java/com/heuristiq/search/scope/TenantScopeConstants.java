package com.heuristiq.search.scope;

/** Fixed values of organization scoping. */
public final class TenantScopeConstants {

    /** Organization owning legacy records with no ownerKey, matching the onboarding service. */
    public static final String DEFAULT_TENANT_ID = "default";

    /** Slot of a role's code in the index (Rider_UAM's UamRole: text1 = code). */
    public static final String ROLE_CODE_FIELD = "text1";

    /** The platform administrator role: owned by the platform, hidden from every organization. */
    public static final String PLATFORM_ADMIN_ROLE = "SUPER_ADMIN";

    private TenantScopeConstants() {
    }
}
