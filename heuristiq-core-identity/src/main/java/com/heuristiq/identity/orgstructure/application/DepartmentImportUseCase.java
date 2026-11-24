package com.heuristiq.uam.orgstructure.application;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentBatch;

/** Use cases of the bulk department import: stage and validate a file, review it, then confirm or cancel. */
public interface DepartmentImportUseCase {

    /**
     * Reads the uploaded file (a document the caller already stored in the document store), validates every row
     * against the organization and the rest of the file and stages the result; changes nothing live.
     */
    DepartmentBatch upload(String tenantId, String actor, String documentId);

    DepartmentBatch batch(String tenantId, String batchId);

    List<DepartmentBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number. */
    List<DepartmentRow> errors(String tenantId, String batchId, int afterRowNo, int limit);

    /** Starts applying the valid rows in the background and returns at once with the batch in APPLYING. */
    DepartmentBatch confirm(String tenantId, String actor, String batchId);

    void cancel(String tenantId, String batchId);
}
