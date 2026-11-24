package com.heuristiq.uam.orgstructure.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.heuristiq.uam.orgstructure.domain.LadderImportModels.ExistingPosition;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderBatch;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderReference;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRow;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.StagedLadderRow;

/**
 * Port to staged ladders files: the batch, its rows and the ladders and positions a file is checked against. Every method that reads
 * or changes a batch takes the organization, so one organization can never see or confirm another's import.
 */
public interface LadderImportStore {

    /** Loads every ladder of the organization with its positions. */
    LadderReference loadReference(String tenantId);

    /** Stores a validated file (all rows, valid or not) as a new VALIDATED batch, remembering the document it came from. */
    LadderBatch stage(String tenantId, String actor, String documentId, String fileName, List<LadderRow> rows);

    Optional<LadderBatch> find(String tenantId, String batchId);

    /** The newest batches first; {@code limit} is capped. */
    List<LadderBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number; {@code limit} is capped. */
    List<LadderRow> errors(String tenantId, String batchId, int afterRowNo, int limit);

    /** VALIDATED or FAILED to APPLYING; false when another request already took it or it is not confirmable. */
    boolean claimForApply(String tenantId, String batchId);

    /** VALIDATED to CANCELLED; false when the batch is not in a cancellable state. */
    boolean cancel(String tenantId, String batchId);

    /** Staged rows of a batch in the given status after {@code afterRowNo}, in row order. */
    List<StagedLadderRow> rows(String batchId, String status, int afterRowNo, int limit);

    void markRows(String batchId, List<Integer> rowNos, String status);

    void addCounts(String batchId, int created, int updated, int laddersCreated);

    void finish(String batchId, String status, String message);

    /** The id of the organization's ladder with this name (ignoring case and surrounding blanks), looked up while applying. */
    Optional<String> findLadderId(String tenantId, String name);

    /** The position of that ladder with this name (ignoring case and surrounding blanks), looked up while applying. */
    Optional<ExistingPosition> findPosition(String tenantId, String ladderId, String name);

    /** True when a ladders import finished applying to the organization after {@code since}. */
    boolean anyAppliedSince(String tenantId, Instant since);
}
