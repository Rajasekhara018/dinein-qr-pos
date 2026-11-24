package com.heuristiq.uam.orgstructure.application;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportBatch;
import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportRow;

/** Use cases of the bulk employee import: stage and validate a file, review it, then confirm or cancel. */
public interface OrgImportUseCase {

    /**
     * Reads the uploaded file (a document the caller already stored in the document store), validates every row
     * against the organization and the rest of the file and stages the result; changes nothing live.
     */
    ImportBatch upload(String tenantId, String actor, String documentId);

    ImportBatch batch(String tenantId, String batchId);

    List<ImportBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number. */
    List<ImportRow> errors(String tenantId, String batchId, int afterRowNo, int limit);

    /** Starts applying the valid rows in the background and returns at once with the batch in APPLYING. */
    ImportBatch confirm(String tenantId, String batchId);

    void cancel(String tenantId, String batchId);
}
