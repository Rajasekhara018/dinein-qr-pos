package com.heuristiq.identity.security;

import java.util.List;

/**
 * What a sign-in or refresh may carry once the status of the person's franchises is applied: the roles that still count,
 * the ids of their ACTIVE franchises (the {@code franchiseIds} claim), and the franchise roles left out because none of
 * their franchises is active. A franchise role gives nothing without an active franchise, so a role left out here is
 * missing from the token's roles and, through them, from the privileges the gateway grants.
 *
 * @param roles          roles written to the token, in the account's order
 * @param franchiseIds   ids of the person's ACTIVE franchises in the session's organization; empty for none
 * @param droppedRoles   franchise roles left out because the person has no active franchise; empty when nothing was left out
 * @param branchIds      ids of the POS branches the person is assigned to in the session's organization (the
 *                       {@code branchIds} claim); empty for none
 */
public record SessionAccess(List<String> roles, List<String> franchiseIds, List<String> droppedRoles, List<String> branchIds) {

    public SessionAccess {
        roles = roles == null ? List.of() : List.copyOf(roles);
        franchiseIds = franchiseIds == null ? List.of() : List.copyOf(franchiseIds);
        droppedRoles = droppedRoles == null ? List.of() : List.copyOf(droppedRoles);
        branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
    }

    /** Without POS branches. */
    public SessionAccess(List<String> roles, List<String> franchiseIds, List<String> droppedRoles) {
        this(roles, franchiseIds, droppedRoles, List.of());
    }

    /** The account's roles unchanged, with no franchises and no branches: for anyone who was not looked up. */
    public static SessionAccess unchanged(List<String> roles) {
        return new SessionAccess(roles, List.of(), List.of(), List.of());
    }

    /** The same access with the given POS branch ids. */
    public SessionAccess withBranchIds(List<String> branchIds) {
        return new SessionAccess(roles, franchiseIds, droppedRoles, branchIds);
    }

    /** True when franchise roles were left out because the person has no active franchise. */
    public boolean franchiseRolesDropped() {
        return !droppedRoles.isEmpty();
    }
}
