package com.heuristiq.identity.pos.application;

import com.heuristiq.identity.pos.domain.Branch;
import com.heuristiq.identity.pos.domain.BranchDraft;
import com.heuristiq.identity.pos.domain.LegalEntity;
import com.heuristiq.identity.pos.domain.LegalEntityDraft;
import java.util.List;
import java.util.Set;

/** Reads and changes a tenant's legal entities, branches and branch staff. Refusals are {@code PosException}s. */
public interface PosOrganizationUseCase {

    List<LegalEntity> legalEntities(PosCaller caller, String tenantId);

    LegalEntity legalEntity(PosCaller caller, String tenantId, String id);

    LegalEntity createLegalEntity(PosCaller caller, String tenantId, LegalEntityDraft draft);

    LegalEntity updateLegalEntity(PosCaller caller, String tenantId, String id, LegalEntityDraft draft, long expectedVersion);

    /** All branches for a tenant-wide caller, otherwise only the caller's assigned branches. */
    List<Branch> branches(PosCaller caller, String tenantId);

    Branch branch(PosCaller caller, String tenantId, String id);

    Branch createBranch(PosCaller caller, String tenantId, BranchDraft draft);

    Branch updateBranch(PosCaller caller, String tenantId, String id, BranchDraft draft, long expectedVersion);

    Set<String> branchStaff(PosCaller caller, String tenantId, String branchId);

    /** Makes {@code userIds} the complete staff of the branch; returns the new staff. */
    Set<String> replaceBranchStaff(PosCaller caller, String tenantId, String branchId, Set<String> userIds);

    /** Ids of the branches a user is assigned to (no caller: used when issuing tokens). */
    List<String> branchIdsOf(String tenantId, String userId);
}
