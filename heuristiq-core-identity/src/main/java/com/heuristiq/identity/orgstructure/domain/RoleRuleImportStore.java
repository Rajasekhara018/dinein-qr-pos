package com.heuristiq.uam.orgstructure.domain;

import java.util.List;
import java.util.Optional;

import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleBatch;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleReference;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.StagedRoleRuleRow;

/**
 * Port to staged roles files: the batch, its rows and the departments, ladders and positions a file is checked against. Every method
 * that reads or changes a batch takes the organization, so one organization can never see or confirm another's import.
 */
public interface RoleRuleImportStore {

    /** Loads the organization's active department codes and every ladder with its positions. */
    RoleRuleReference loadReference(String tenantId);

    /** Stores a validated file (all rows, valid or not) as a new VALIDATED batch, remembering the document it came from. */
    RoleRuleBatch stage(String tenantId, String actor, String documentId, String fileName, List<RoleRuleRow> rows);

    Optional<RoleRuleBatch> find(String tenantId, String batchId);

    /** The newest batches first; {@code limit} is capped. */
    List<RoleRuleBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number; {@code limit} is capped. */
    List<RoleRuleRow> errors(String tenantId, String batchId, int afterRowNo, int limit);

    /** VALIDATED or FAILED to APPLYING; false when another request already took it or it is not confirmable. */
    boolean claimForApply(String tenantId, String batchId);

    /** VALIDATED to CANCELLED; false when the batch is not in a cancellable state. */
    boolean cancel(String tenantId, String batchId);

    /** Staged rows of a batch in the given status after {@code afterRowNo}, in row order. */
    List<StagedRoleRuleRow> rows(String batchId, String status, int afterRowNo, int limit);

    void markRows(String batchId, List<Integer> rowNos, String status);

    void addCounts(String batchId, int created, int unchanged);

    void finish(String batchId, String status, String message);

    /** The id of the organization's active department with this code, looked up while applying. */
    Optional<String> findUnitId(String tenantId, String code);

    /** The id of the position (by name, ignoring case and blanks) in the ladder (by name, ignoring case and blanks), looked up while applying. */
    Optional<String> findPositionId(String tenantId, String ladderName, String positionName);
}
