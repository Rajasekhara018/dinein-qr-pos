package com.heuristiq.identity.pos.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Which users work at which branch. */
public interface BranchStaffStore {

    Set<String> staffOf(String tenantId, String branchId);

    /** Makes {@code userIds} the complete staff of the branch. */
    void replaceStaff(String tenantId, String branchId, Set<String> userIds, String actor, Instant at);

    /** Ids of the branches the user is assigned to in the tenant. */
    List<String> branchIdsOf(String tenantId, String userId);
}
