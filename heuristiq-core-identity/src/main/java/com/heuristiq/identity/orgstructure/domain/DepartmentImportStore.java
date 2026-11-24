package com.heuristiq.uam.orgstructure.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentBatch;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentReference;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.StagedDepartmentRow;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.UnitState;

/**
 * Port to staged department files: the batch, its rows and the departments a file is checked against. Every method that reads or
 * changes a batch takes the organization, so one organization can never see or confirm another's import.
 */
public interface DepartmentImportStore {

    /** Loads every department of the organization (active and archived) by code, with its parent's code. */
    DepartmentReference loadReference(String tenantId);

    /** Stores a validated file (all rows, valid or not) as a new VALIDATED batch, remembering the document it came from and how many new departments have no ladder. */
    DepartmentBatch stage(String tenantId, String actor, String documentId, String fileName, List<DepartmentRow> rows,
                            int withoutLadder);

    Optional<DepartmentBatch> find(String tenantId, String batchId);

    /** The newest batches first; {@code limit} is capped. */
    List<DepartmentBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number; {@code limit} is capped. */
    List<DepartmentRow> errors(String tenantId, String batchId, int afterRowNo, int limit);

    /** VALIDATED or FAILED to APPLYING; false when another request already took it or it is not confirmable. */
    boolean claimForApply(String tenantId, String batchId);

    /** VALIDATED to CANCELLED; false when the batch is not in a cancellable state. */
    boolean cancel(String tenantId, String batchId);

    /** Staged rows of a batch in the given status after {@code afterRowNo}, in row order. */
    List<StagedDepartmentRow> rows(String batchId, String status, int afterRowNo, int limit);

    void markRows(String batchId, List<Integer> rowNos, String status);

    void addCounts(String batchId, int created, int updated);

    void finish(String batchId, String status, String message);

    /** The department with this code as it is now (active or archived), looked up while applying. */
    Optional<UnitState> findUnit(String tenantId, String code);

    /** The id of the organization's position ladder with this name (ignoring case and surrounding blanks), looked up while applying. */
    Optional<String> findLadderId(String tenantId, String name);

    /** True when a department import finished applying to the organization after {@code since}. */
    boolean anyAppliedSince(String tenantId, Instant since);
}
