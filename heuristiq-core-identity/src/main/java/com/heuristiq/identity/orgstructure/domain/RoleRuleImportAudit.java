package com.heuristiq.identity.orgstructure.domain;

/** Port to the audit trail: one domain event for every roles import that ends (applied or failed). */
public interface RoleRuleImportAudit {

    /**
     * Records how an import ended. A failure to write the entry never fails the import.
     *
     * @param success true when the batch was applied, false when it failed
     * @param message the failure reason, null on success
     */
    void record(String tenantId, String actor, String batchId, boolean success, String fileName, int totalRows, int validRows,
                int created, int unchanged, String message);
}
