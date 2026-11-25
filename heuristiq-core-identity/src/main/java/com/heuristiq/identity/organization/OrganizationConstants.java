package com.heuristiq.identity.organization;

/** Fixed values of organization settings. */
public final class OrganizationConstants {

    /** Settings key of how much of the organization a person sees: {@link DataScopeMode}. */
    public static final String DATA_SCOPE_KEY = "dataScope";

    /** Settings key of the opt-in that lists the organization on the public self-signup choice (boolean, absent means off). */
    public static final String OPEN_FOR_SELF_SIGNUP_KEY = "openForSelfSignup";

    private OrganizationConstants() {
    }
}
