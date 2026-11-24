package com.heuristiq.uam.organization;

import java.util.Map;

/** Reads the organization settings kept in {@code Tenant.settings}. */
public final class OrganizationSettings {

    private OrganizationSettings() {
    }

    /** True only when the organization explicitly opted in to being offered on the public self-signup list; off by default. */
    public static boolean openForSelfSignup(Map<String, Object> settings) {
        Object value = settings == null ? null : settings.get(OrganizationConstants.OPEN_FOR_SELF_SIGNUP_KEY);
        return value != null && Boolean.parseBoolean(String.valueOf(value).trim());
    }

    /** How much of the organization people see; the whole organization unless the organization chose otherwise. */
    public static DataScopeMode dataScopeOf(Map<String, Object> settings) {
        Object value = settings == null ? null : settings.get(OrganizationConstants.DATA_SCOPE_KEY);
        if (value == null) {
            return DataScopeMode.ORGANIZATION;
        }
        try {
            return DataScopeMode.valueOf(String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DataScopeMode.ORGANIZATION;
        }
    }
}
