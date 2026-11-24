package com.heuristiq.uam.orgstructure.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.heuristiq.uam.orgstructure.domain.ImportLimits;
import com.heuristiq.uam.orgstructure.domain.LoginAccounts;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.RoleRuleFileSource;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportAudit;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleBatch;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleFile;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleReference;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.StagedRoleRuleRow;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportStore;
import com.heuristiq.uam.orgstructure.domain.RoleRuleRowValidator;

/**
 * Bulk roles import. An upload is validated in full and staged without touching the organization; only a confirmed batch is applied, in
 * short transactions of {@code chunkSize} rows on the same bounded background worker the other imports use. Every rule is added through
 * {@link OrgLoginUseCase#createRule}, the very method the logins screen uses, so the role check and the "rule already exists" check are the
 * same ones; a rule that already exists is counted as unchanged and nothing is ever deleted or changed. Applying is idempotent, so a failed
 * batch can be confirmed again and carries on where it stopped.
 */
@Service
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class RoleRuleImportService implements RoleRuleImportUseCase {
    private static final Logger log = LoggerFactory.getLogger(RoleRuleImportService.class);

    private final RoleRuleImportStore imports;
    private final RoleRuleFileSource files;
    private final OrgLoginUseCase logins;
    private final LoginAccounts accounts;
    private final ImportLimits limits;
    private final ExecutorService worker;
    private final TransactionTemplate tx;
    private final RoleRuleImportAudit audit;

    public RoleRuleImportService(RoleRuleImportStore imports, RoleRuleFileSource files, OrgLoginUseCase logins, LoginAccounts accounts,
                                 ImportLimits limits, @Qualifier("orgImportExecutor") ExecutorService worker,
                                 PlatformTransactionManager transactionManager, RoleRuleImportAudit audit) {
        this.imports = imports;
        this.files = files;
        this.logins = logins;
        this.accounts = accounts;
        this.limits = limits;
        this.worker = worker;
        this.tx = new TransactionTemplate(transactionManager);
        this.audit = audit;
    }

    @Override
    public RoleRuleBatch upload(String tenantId, String actor, String documentId) {
        RoleRuleFile file = files.fetchRoleRules(documentId, actor);
        List<RoleRuleRowInput> rows = file.rows();
        if (rows.isEmpty()) {
            throw OrgStructureException.invalid("The file has no data rows");
        }
        if (rows.size() > limits.maxRows()) {
            throw OrgStructureException.invalid("The file has " + rows.size() + " rows; at most " + limits.maxRows()
                    + " are allowed per import");
        }
        RoleRuleReference reference = imports.loadReference(tenantId);
        List<RoleRuleRow> validated = RoleRuleRowValidator.validate(rows, reference, accounts.roleCodes(tenantId));
        return imports.stage(tenantId, actor, file.documentId(), file.fileName(), validated);
    }

    @Override
    public RoleRuleBatch batch(String tenantId, String batchId) {
        return imports.find(tenantId, batchId)
                .orElseThrow(() -> OrgStructureException.notFound("Import not found: " + batchId));
    }

    @Override
    public List<RoleRuleBatch> recent(String tenantId, int limit) {
        return imports.recent(tenantId, limit);
    }

    @Override
    public List<RoleRuleRow> errors(String tenantId, String batchId, int afterRowNo, int limit) {
        batch(tenantId, batchId);
        return imports.errors(tenantId, batchId, afterRowNo, limit);
    }

    @Override
    public RoleRuleBatch confirm(String tenantId, String actor, String batchId) {
        RoleRuleBatch batch = batch(tenantId, batchId);
        if (batch.validRows() == 0) {
            throw OrgStructureException.invalid("There are no valid rows to import");
        }
        if (!imports.claimForApply(tenantId, batchId)) {
            throw OrgStructureException.conflict("Import is " + batch.status() + " and cannot be confirmed");
        }
        try {
            worker.execute(() -> run(tenantId, actor, batchId));
        } catch (RejectedExecutionException e) {
            imports.finish(batchId, OrgStructureConstants.IMPORT_FAILED, "The import queue is full; confirm again in a few minutes");
            throw OrgStructureException.conflict("Another import is running; confirm again in a few minutes");
        }
        return batch(tenantId, batchId);
    }

    @Override
    public void cancel(String tenantId, String batchId) {
        batch(tenantId, batchId);
        if (!imports.cancel(tenantId, batchId)) {
            throw OrgStructureException.conflict("Only a validated import can be cancelled");
        }
    }

    // ---- applying ----------------------------------------------------------------------------

    /** Runs on the background worker; any failure ends the batch as FAILED so it can be confirmed again. */
    void run(String tenantId, String actor, String batchId) {
        boolean success = false;
        String message = null;
        try {
            int after = 0;
            while (true) {
                List<StagedRoleRuleRow> chunk = imports.rows(batchId, OrgStructureConstants.IMPORT_ROW_VALID, after, limits.chunkSize());
                if (chunk.isEmpty()) {
                    break;
                }
                tx.executeWithoutResult(status -> applyChunk(tenantId, actor, batchId, chunk));
                after = chunk.get(chunk.size() - 1).input().rowNo();
            }
            imports.finish(batchId, OrgStructureConstants.IMPORT_APPLIED, null);
            success = true;
        } catch (RuntimeException e) {
            log.warn("Roles import {} failed: {}", batchId, e.getMessage());
            message = truncate(e.getMessage());
            imports.finish(batchId, OrgStructureConstants.IMPORT_FAILED, message);
        }
        RoleRuleBatch ended = imports.find(tenantId, batchId).orElse(null);
        if (ended != null) {
            audit.record(tenantId, actor, batchId, success, ended.fileName(), ended.totalRows(), ended.validRows(), ended.createdCount(),
                    ended.unchangedCount(), message);
        }
    }

    private void applyChunk(String tenantId, String actor, String batchId, List<StagedRoleRuleRow> chunk) {
        int created = 0;
        int unchanged = 0;
        List<Integer> done = new ArrayList<>();
        for (StagedRoleRuleRow staged : chunk) {
            RoleRuleRowInput row = staged.input();
            try {
                String unitId = row.departmentCode() == null ? null : imports.findUnitId(tenantId, row.departmentCode())
                        .orElseThrow(() -> OrgStructureException.invalid("Unknown department " + row.departmentCode()));
                String positionId = row.positionName() == null ? null : imports.findPositionId(tenantId, row.ladder(), row.positionName())
                        .orElseThrow(() -> OrgStructureException.invalid("Unknown position " + row.positionName()));
                try {
                    logins.createRule(tenantId, actor, unitId, positionId, row.roleCode());
                    created++;
                } catch (OrgStructureException e) {
                    if (e.kind() != OrgStructureException.Kind.CONFLICT) {
                        throw e;
                    }
                    unchanged++;   // the rule already exists: nothing to add
                }
                done.add(row.rowNo());
            } catch (OrgStructureException e) {
                throw OrgStructureException.invalid("Row " + row.rowNo() + ": " + e.getMessage());
            }
        }
        if (!done.isEmpty()) {
            imports.markRows(batchId, done, OrgStructureConstants.IMPORT_ROW_APPLIED);
            imports.addCounts(batchId, created, unchanged);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
