package com.heuristiq.identity.pos.application.port;

import com.heuristiq.identity.pos.domain.Branch;
import java.util.List;
import java.util.Optional;

/** Persistence of a tenant's branches. Every call is scoped to one tenant. */
public interface BranchStore {

    Optional<Branch> find(String tenantId, String id);

    List<Branch> list(String tenantId);

    /** True when at least one branch belongs to the legal entity. */
    boolean anyForLegalEntity(String tenantId, String legalEntityId);

    /** Stores a new branch; throws {@code PosException.conflict("pos.branch.codeTaken", "code")} when the tenant already has the code. */
    Branch insert(Branch branch);

    /**
     * Replaces the stored branch with {@code branch} (which carries the new version) when the stored version equals
     * {@code expectedVersion}, else throws {@code PosException.conflict("pos.staleVersion", "version")}. Throws
     * {@code PosException.conflict("pos.branch.codeTaken", "code")} when another branch of the tenant has the code.
     */
    Branch update(Branch branch, long expectedVersion);
}
