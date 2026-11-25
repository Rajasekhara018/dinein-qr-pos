package com.heuristiq.identity.orgstructure.domain;

import java.util.List;

/**
 * Operations a draft can hold. Each type lists the payload keys it requires so a malformed change
 * is rejected when it is added, not when the draft is published.
 */
public enum ChangeType {
    UNIT_CREATE(true, "id", "code", "name"),
    UNIT_RENAME(false, "id", "name"),
    UNIT_MOVE(false, "id"),
    UNIT_ARCHIVE(false, "id"),
    UNIT_SET_LADDER(false, "unitId", "ladderId"),
    UNIT_SET_HEAD(false, "unitId", "employeeId"),
    UNIT_CLEAR_HEAD(false, "unitId"),
    LADDER_CREATE(true, "id", "name"),
    LADDER_COPY(true, "id", "sourceLadderId", "name"),
    POSITION_UPSERT(true, "id", "ladderId", "name", "rank"),
    POSITION_REMOVE(false, "id"),
    ASSIGN(true, "id", "employeeId", "unitId", "positionId"),
    UNASSIGN(false, "employeeId", "unitId"),
    SET_REPORTS_TO(false, "employeeId");

    private final boolean generatesId;
    private final List<String> requiredKeys;

    ChangeType(boolean generatesId, String... requiredKeys) {
        this.generatesId = generatesId;
        this.requiredKeys = List.of(requiredKeys);
    }

    /** True when the service fills in {@code id} for the caller so later changes can refer to the new record. */
    public boolean generatesId() {
        return generatesId;
    }

    public List<String> requiredKeys() {
        return requiredKeys;
    }
}
