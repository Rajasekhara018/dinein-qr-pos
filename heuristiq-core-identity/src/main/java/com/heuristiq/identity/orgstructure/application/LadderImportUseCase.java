package com.heuristiq.uam.orgstructure.application;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderBatch;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRow;

/** Use cases of the bulk ladders and positions import: stage and validate a file, review it, then confirm or cancel. */
public interface LadderImportUseCase {

    /**
     * Reads the uploaded file (a document the caller already stored in the document store), validates every row against the
     * organization's ladders and the rest of the file and stages the result; changes nothing live.
     */
    LadderBatch upload(String tenantId, String actor, String documentId);

    LadderBatch batch(String tenantId, String batchId);

    List<LadderBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number. */
    List<LadderRow> errors(String tenantId, String batchId, int afterRowNo, int limit);

    /** Starts applying the valid rows in the background and returns at once with the batch in APPLYING. */
    LadderBatch confirm(String tenantId, String actor, String batchId);

    void cancel(String tenantId, String batchId);
}
