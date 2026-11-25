package com.heuristiq.identity.orgstructure.domain;

import java.util.List;
import java.util.Optional;

import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportBatch;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportReference;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRow;
import com.heuristiq.identity.orgstructure.domain.ImportModels.StagedRow;

/** Port to staged import files: the batch, its rows and the reference data rows are checked against. */
public interface ImportStore {

    /** Loads the departments, positions, employees and reporting lines a file is validated against. */
    ImportReference loadReference(String tenantId);

    /** Stores a validated file (all rows, valid or not) as a new VALIDATED batch, remembering the document it came from. */
    ImportBatch stage(String tenantId, String actor, String documentId, String fileName, List<ImportRow> rows);

    Optional<ImportBatch> find(String tenantId, String batchId);

    List<ImportBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number. */
    List<ImportRow> errors(String batchId, int afterRowNo, int limit);

    /** VALIDATED to APPLYING; false when another request already took it or it is not confirmable. */
    boolean claimForApply(String tenantId, String batchId);

    /** VALIDATED to CANCELLED; false when the batch is not in a cancellable state. */
    boolean cancel(String tenantId, String batchId);

    /** Staged rows in the given status after {@code afterRowNo}, in row order. */
    List<StagedRow> rows(String batchId, String status, int afterRowNo, int limit);

    void markRows(String batchId, List<Integer> rowNos, String status);

    void addCounts(String batchId, int created, int updated);

    /** Adds to the number of department heads this batch has set. */
    void addHeadsSet(String batchId, int heads);

    void finish(String batchId, String status, String message);

    /** True when an import batch finished applying employees to the tenant after {@code since}. */
    boolean anyAppliedSince(String tenantId, java.time.Instant since);
}
