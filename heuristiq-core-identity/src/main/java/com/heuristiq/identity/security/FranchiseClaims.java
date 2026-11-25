package com.heuristiq.identity.security;

import java.util.List;

/**
 * The franchises a person owns or works for, written into their token so every service can limit what they see to those
 * franchises. Implemented where franchises are kept; an organization without franchises has none.
 */
public interface FranchiseClaims {

    /**
     * Ids of the active franchises of the organization that the account owns or is currently an agent of; empty for anyone
     * in no franchise. A failure must be thrown, never answered with an empty list: a franchise person whose token had no
     * franchises would be treated as unrestricted.
     */
    List<String> franchiseIdsOf(String tenantId, String userId);

    /**
     * The franchises of {@link #franchiseIdsOf} split into the ones the person owns and the ones where they are an active agent. This only
     * describes the person (it never restricts anything), so unlike {@link #franchiseIdsOf} a failure may be answered with an empty split.
     *
     * @param owned franchise ids the person owns
     * @param agent franchise ids where the person is an active agent
     */
    record RoleIds(List<String> owned, List<String> agent) {

        public RoleIds {
            owned = owned == null ? List.of() : List.copyOf(owned);
            agent = agent == null ? List.of() : List.copyOf(agent);
        }
    }

    /** @return the owner and agent franchises of the person; empty lists when unknown (the default, and a lookup that could not be made) */
    default RoleIds roleIdsOf(String tenantId, String userId) {
        return new RoleIds(List.of(), List.of());
    }

    /**
     * True when {@link #franchiseIdsOf} really asks where franchises are kept, so an empty answer means "no active franchise".
     * False for a setup without franchises (the lookup is switched off): an empty answer then says nothing about the
     * person's franchises, and their franchise roles are not taken away.
     */
    default boolean lookupEnabled() {
        return true;
    }
}
