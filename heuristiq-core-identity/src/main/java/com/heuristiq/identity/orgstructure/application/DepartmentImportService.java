package com.heuristiq.identity.orgstructure.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.heuristiq.identity.orgstructure.domain.ChangeType;
import com.heuristiq.identity.orgstructure.domain.DepartmentFileSource;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportAudit;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportConstants;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentBatch;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentFile;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentReference;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRowInput;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.StagedDepartmentRow;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.UnitState;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportStore;
import com.heuristiq.identity.orgstructure.domain.DepartmentRowValidator;
import com.heuristiq.identity.orgstructure.domain.ImportLimits;
import com.heuristiq.identity.orgstructure.domain.Models.Change;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;

/**
 * Bulk department import. An upload is validated in full and staged without touching the live organization; only a confirmed batch
 * is applied, in short transactions of {@code chunkSize} rows on the same bounded background worker the employee import uses, so
 * imports of one organization never run at the same time. Departments are created and changed through the same
 * {@link OrgStructureStore#apply} operations the org builder's publish uses ({@code UNIT_CREATE}, {@code UNIT_RENAME},
 * {@code UNIT_MOVE}), which keep the department closure table right. Parents are applied before their children: each pass creates the
 * rows whose parent now exists. Applying is idempotent by code, so a failed batch can be confirmed again and carries on where it stopped.
 */
@Service
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class DepartmentImportService implements DepartmentImportUseCase {
    private static final Logger log = LoggerFactory.getLogger(DepartmentImportService.class);

    private final DepartmentImportStore imports;
    private final DepartmentFileSource files;
    private final OrgStructureStore structure;
    private final ImportLimits limits;
    private final ExecutorService worker;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final DepartmentImportAudit audit;

    public DepartmentImportService(DepartmentImportStore imports, DepartmentFileSource files, OrgStructureStore structure,
                                   ImportLimits limits, @Qualifier("orgImportExecutor") ExecutorService worker,
                                   PlatformTransactionManager transactionManager, Clock clock, ApplicationEventPublisher events,
                                   DepartmentImportAudit audit) {
        this.imports = imports;
        this.files = files;
        this.structure = structure;
        this.limits = limits;
        this.worker = worker;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.events = events;
        this.audit = audit;
    }

    @Override
    public DepartmentBatch upload(String tenantId, String actor, String documentId) {
        DepartmentFile file = files.fetchDepartments(documentId, actor);
        List<DepartmentRowInput> rows = file.rows();
        if (rows.isEmpty()) {
            throw OrgStructureException.invalid("The file has no data rows");
        }
        if (rows.size() > limits.maxRows()) {
            throw OrgStructureException.invalid("The file has " + rows.size() + " rows; at most " + limits.maxRows()
                    + " are allowed per import");
        }
        DepartmentReference reference = imports.loadReference(tenantId);
        List<DepartmentRow> validated = DepartmentRowValidator.validate(rows, reference);
        return imports.stage(tenantId, actor, file.documentId(), file.fileName(), validated,
                DepartmentRowValidator.countWithoutLadder(validated, reference));
    }

    @Override
    public DepartmentBatch batch(String tenantId, String batchId) {
        return imports.find(tenantId, batchId)
                .orElseThrow(() -> OrgStructureException.notFound("Import not found: " + batchId));
    }

    @Override
    public List<DepartmentBatch> recent(String tenantId, int limit) {
        return imports.recent(tenantId, limit);
    }

    @Override
    public List<DepartmentRow> errors(String tenantId, String batchId, int afterRowNo, int limit) {
        batch(tenantId, batchId);
        return imports.errors(tenantId, batchId, afterRowNo, limit);
    }

    @Override
    public DepartmentBatch confirm(String tenantId, String actor, String batchId) {
        DepartmentBatch batch = batch(tenantId, batchId);
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
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            for (int pass = 0; pass < DepartmentImportConstants.MAX_APPLY_PASSES; pass++) {
                if (applyPass(tenantId, batchId, today) == 0) {
                    break;
                }
            }
            if (!imports.rows(batchId, OrgStructureConstants.IMPORT_ROW_VALID, 0, 1).isEmpty()) {
                throw OrgStructureException.invalid("Some departments were not created because their parent department could not be found");
            }
            imports.finish(batchId, OrgStructureConstants.IMPORT_APPLIED, null);
            success = true;
        } catch (RuntimeException e) {
            log.warn("Department import {} failed: {}", batchId, e.getMessage());
            message = truncate(e.getMessage());
            imports.finish(batchId, OrgStructureConstants.IMPORT_FAILED, message);
        }
        afterRun(tenantId, actor, batchId, success, message);
    }

    /** Tells the rest of the platform the structure changed (when anything did) and writes the audit entry. */
    private void afterRun(String tenantId, String actor, String batchId, boolean success, String message) {
        DepartmentBatch ended = imports.find(tenantId, batchId).orElse(null);
        if (ended == null) {
            return;
        }
        if (ended.createdCount() + ended.updatedCount() > 0) {
            events.publishEvent(new OrgStructureChanged(tenantId));
        }
        audit.record(tenantId, actor, batchId, success, ended.fileName(), ended.totalRows(), ended.validRows(),
                ended.createdCount(), ended.updatedCount(), message);
    }

    /** One walk over the rows still to do; returns how many departments it applied (parents whose own parent is missing wait for the next pass). */
    private int applyPass(String tenantId, String batchId, LocalDate today) {
        int applied = 0;
        int after = 0;
        while (true) {
            List<StagedDepartmentRow> chunk = imports.rows(batchId, OrgStructureConstants.IMPORT_ROW_VALID, after, limits.chunkSize());
            if (chunk.isEmpty()) {
                return applied;
            }
            Integer done = tx.execute(status -> applyChunk(tenantId, batchId, chunk, today));
            applied += done == null ? 0 : done;
            after = chunk.get(chunk.size() - 1).input().rowNo();
        }
    }

    private int applyChunk(String tenantId, String batchId, List<StagedDepartmentRow> chunk, LocalDate today) {
        int created = 0;
        int updated = 0;
        List<Integer> done = new ArrayList<>();
        for (StagedDepartmentRow staged : chunk) {
            DepartmentRowInput row = staged.input();
            try {
                Optional<UnitState> parent = row.parentCode() == null ? Optional.empty() : imports.findUnit(tenantId, row.parentCode());
                if (row.parentCode() != null && parent.filter(UnitState::active).isEmpty()) {
                    continue;   // the parent is not there yet: a later pass (after its own row) picks this one up
                }
                String namedLadder = namedLadderId(tenantId, row);
                Optional<UnitState> own = imports.findUnit(tenantId, row.code());
                if (own.isEmpty()) {
                    create(tenantId, row, parent.orElse(null), namedLadder, today);
                    created++;
                } else if (update(tenantId, row, own.get(), parent.orElse(null), namedLadder, today)) {
                    updated++;
                }
                done.add(row.rowNo());
            } catch (OrgStructureException e) {
                throw OrgStructureException.invalid("Row " + row.rowNo() + ": " + e.getMessage());
            }
        }
        if (!done.isEmpty()) {
            imports.markRows(batchId, done, OrgStructureConstants.IMPORT_ROW_APPLIED);
            imports.addCounts(batchId, created, updated);
        }
        return done.size();
    }

    /** The id of the ladder the row names (null when it names none); a ladder removed since the file was checked fails the row. */
    private String namedLadderId(String tenantId, DepartmentRowInput row) {
        if (row.ladder() == null) {
            return null;
        }
        return imports.findLadderId(tenantId, row.ladder())
                .orElseThrow(() -> OrgStructureException.invalid("Unknown ladder " + row.ladder()));
    }

    /** A new department. It has the ladder the row names, else its parent's, so people can be placed in it straight away. */
    private void create(String tenantId, DepartmentRowInput row, UnitState parent, String namedLadder, LocalDate today) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("code", row.code());
        payload.put("name", row.name());
        if (row.unitType() != null) {
            payload.put("unitType", row.unitType());
        }
        if (parent != null) {
            payload.put("parentId", parent.id());
        }
        String ladderId = namedLadder != null ? namedLadder : parent == null ? null : parent.ladderId();
        if (ladderId != null) {
            payload.put("ladderId", ladderId);
        }
        structure.apply(tenantId, new Change(null, null, 0, ChangeType.UNIT_CREATE, payload), today);
    }

    /**
     * An existing department: its name follows the file, and its parent too when the file names one that differs (a blank parent
     * leaves it where it is) and its ladder when the file names one (a blank ladder leaves it). Its unit type is not changed, as the org builder cannot change it either. Returns whether anything changed.
     */
    private boolean update(String tenantId, DepartmentRowInput row, UnitState own, UnitState parent, String namedLadder,
                           LocalDate today) {
        if (!own.active()) {
            throw OrgStructureException.invalid("The department " + row.code() + " has been archived");
        }
        boolean changed = false;
        if (!row.name().equals(own.name())) {
            structure.apply(tenantId, new Change(null, null, 0, ChangeType.UNIT_RENAME, Map.of("id", own.id(), "name", row.name())), today);
            changed = true;
        }
        if (parent != null && !parent.id().equals(own.parentId())) {
            structure.apply(tenantId, new Change(null, null, 0, ChangeType.UNIT_MOVE, Map.of("id", own.id(), "newParentId", parent.id())), today);
            changed = true;
        }
        if (namedLadder != null && !namedLadder.equals(own.ladderId())) {
            structure.apply(tenantId, new Change(null, null, 0, ChangeType.UNIT_SET_LADDER,
                    Map.of("unitId", own.id(), "ladderId", namedLadder)), today);
            changed = true;
        }
        return changed;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
