package com.heuristiq.payment.security;


/**
 * The authenticated caller: user id plus role claim (or the backend {@code SERVICE} identity).
 *
 * @param userId the JWT subject (customer id, driver id, admin id), or {@code internal-service}
 * @param role   the JWT role claim, or {@code SERVICE}
 * @param elevated true when the gateway vouched that the caller holds the elevated-access privilege
 *                 (see {@link GatewayElevation}); replaces checks of admin role names
 * @param tenantId the organization of the caller's session (the JWT's {@code tenantId} claim; for the backend
 *                 {@code SERVICE} identity, the organization it forwards on behalf of a user, if any)
 */
public record AuthPrincipal(String userId, String role, boolean elevated, String tenantId) {

    /** A caller the gateway did not mark as elevated. */
    public AuthPrincipal(String userId, String role) {
        this(userId, role, false, null);
    }

    public AuthPrincipal(String userId, String role, boolean elevated) {
        this(userId, role, elevated, null);
    }

    /** The backend service identity (service token), which acts on any user's data. */
    public static final String SERVICE_ROLE = "SERVICE";

    /** Trusted in-process caller (tests, internal flows) that is exempt from ownership checks. */
    public static final AuthPrincipal SYSTEM = new AuthPrincipal("internal-service", "SERVICE");

    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    public boolean isDriver() {
        return "DRIVER".equals(role);
    }

    /** True for gateway-elevated callers and the backend SERVICE identity. */
    public boolean isPrivileged() {
        return elevated || SERVICE_ROLE.equals(role);
    }

    /** True when this caller's user id equals {@code ownerId}. */
    public boolean isSelf(String ownerId) {
        return userId != null && userId.equals(ownerId);
    }
}
