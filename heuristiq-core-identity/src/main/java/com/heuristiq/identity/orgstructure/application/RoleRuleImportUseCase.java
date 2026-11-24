package com.heuristiq.uam.orgstructure.application;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleBatch;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;

/** Use cases of the bulk roles import: stage and validate a file, review it, then confirm or cancel. */
public interface RoleRuleImportUseCase {

    /**
     * Reads the uploaded file (a document the caller already stored in the document store), validates every row against the
     * organization and the rest of the file and stages the result; changes nothing live.
     */
    RoleRuleBatch upload(String tenantId, String actor, String documentId);

    RoleRuleBatch batch(String tenantId, String batchId);

    List<RoleRuleBatch> recent(String tenantId, int limit);

    /** Rejected rows with their error codes, keyset-paginated by row number. */
    List<RoleRuleRow> errors(String tenantId, String batchId, int afterRowNo, int limit);

    /** Starts adding the valid rows' rules in the background and returns at once with the batch in APPLYING. */
    RoleRuleBatch confirm(String tenantId, String actor, String batchId);

    void cancel(String tenantId, String batchId);
}
