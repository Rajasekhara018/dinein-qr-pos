package com.heuristiq.identity.pos.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared (organization-less) POS system roles and the privileges each is seeded with. Privileges are the privilege.json
 * names, the form the structural roles of {@code BootstrapRoleConstants} store. The roles only ever gain privileges at start-up
 * (see {@code PosRoleBootstrap}); the DineIn ordering privileges of billing, waiter and kitchen staff arrive with the ordering plan.
 */
public final class PosRoles {

    public static final String RESTAURANT_ADMIN = "POS_RESTAURANT_ADMIN";
    public static final String BRANCH_ADMIN = "POS_BRANCH_ADMIN";
    public static final String BILLING = "POS_BILLING";
    public static final String WAITER = "POS_WAITER";
    public static final String KITCHEN = "POS_KITCHEN";

    private static final List<String> BRANCH_VIEW = List.of("POS_BRANCH_LIST", "POS_BRANCH_READ");

    /** Every POS role with its privileges, in seeding order. */
    public static final Map<String, List<String>> PRIVILEGES = buildPrivileges();

    private PosRoles() {
    }

    private static Map<String, List<String>> buildPrivileges() {
        Map<String, List<String>> privileges = new LinkedHashMap<>();
        privileges.put(RESTAURANT_ADMIN, List.of(
                "POS_LEGAL_ENTITY_LIST", "POS_LEGAL_ENTITY_READ", "POS_LEGAL_ENTITY_CREATE", "POS_LEGAL_ENTITY_UPDATE",
                "POS_BRANCH_LIST", "POS_BRANCH_READ", "POS_BRANCH_CREATE", "POS_BRANCH_UPDATE",
                "POS_BRANCH_STAFF_READ", "POS_BRANCH_STAFF_UPDATE"));
        privileges.put(BRANCH_ADMIN, List.of(
                "POS_LEGAL_ENTITY_READ", "POS_BRANCH_LIST", "POS_BRANCH_READ", "POS_BRANCH_STAFF_READ", "POS_BRANCH_STAFF_UPDATE"));
        privileges.put(BILLING, BRANCH_VIEW);
        privileges.put(WAITER, BRANCH_VIEW);
        privileges.put(KITCHEN, BRANCH_VIEW);
        return java.util.Collections.unmodifiableMap(privileges);
    }
}
