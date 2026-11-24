package com.heuristiq.gateway.audit;

/**
 * Exchange attribute names through which {@code PrivilegeBasedAccessFilter} tells the audit publisher what it
 * decided. They are set on the exchange attribute map, which every mutated copy of the exchange shares.
 */
public final class AuditExchangeAttributes {

    private AuditExchangeAttributes() {
    }

    /** {@code Set<Integer>}: the privilege ids that matched the request (granted ones when allowed, required ones when denied). */
    public static final String PRIVILEGE_IDS = AuditExchangeAttributes.class.getName() + ".privilegeIds";
    /** {@code String}: catalog module of the matched entry. */
    public static final String MODULE = AuditExchangeAttributes.class.getName() + ".module";
    /** {@code String}: catalog service slug the request resolved to (e.g. rider-config). */
    public static final String SERVICE = AuditExchangeAttributes.class.getName() + ".service";
    /** {@code Boolean}: true when the privilege check refused the request. */
    public static final String DENIED = AuditExchangeAttributes.class.getName() + ".denied";
}
