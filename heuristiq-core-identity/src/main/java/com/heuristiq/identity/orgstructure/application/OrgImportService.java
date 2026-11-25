package com.heuristiq.identity.orgstructure.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.heuristiq.identity.orgstructure.domain.ChangeType;
import com.heuristiq.identity.orgstructure.domain.EmployeeStore;
import com.heuristiq.identity.orgstructure.domain.ImportFileSource;
import com.heuristiq.identity.orgstructure.domain.ImportFlags;
import com.heuristiq.identity.orgstructure.domain.ImportLimits;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportBatch;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportReference;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRow;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRowInput;
import com.heuristiq.identity.orgstructure.domain.ImportModels.StagedRow;
import com.heuristiq.identity.orgstructure.domain.ImportModels.UnitRef;
import com.heuristiq.identity.orgstructure.domain.ImportRowValidator;
import com.heuristiq.identity.orgstructure.domain.ImportStore;
import com.heuristiq.identity.orgstructure.domain.Models.Change;
import com.heuristiq.identity.orgstructure.domain.Models.Employee;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;

/**
 * Bulk employee import. An upload is validated in full and staged without touching the live
 * organization; only a confirmed batch is applied, in short transactions of {@code chunkSize} rows on a
 * bounded background worker. Applying is idempotent: rows are matched by employee code and a row's stage
 * is recorded, so a failed batch can be confirmed again and carries on where it stopped.
 */
@Service
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class OrgImportService implements OrgImportUseCase {
    private static final Logger log = LoggerFactory.getLogger(OrgImportService.class);

    private final ImportStore imports;
    private final ImportFileSource files;
    private final EmployeeStore employees;
    private final OrgStructureStore structure;
    private final ImportLimits limits;
    private final ExecutorService worker;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrgImportService(ImportStore imports, ImportFileSource files, EmployeeStore employees, OrgStructureStore structure,
                            ImportLimits limits, @Qualifier("orgImportExecutor") ExecutorService worker,
                            PlatformTransactionManager transactionManager, Clock clock) {
        this.imports = imports;
        this.files = files;
        this.employees = employees;
        this.structure = structure;
        this.limits = limits;
        this.worker = worker;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public ImportBatch upload(String tenantId, String actor, String documentId) {
        ImportFileSource.ImportFile file = files.fetch(documentId, actor);
        List<ImportRowInput> rows = file.rows();
        if (rows.isEmpty()) {
            throw OrgStructureException.invalid("The file has no data rows");
        }
        if (rows.size() > limits.maxRows()) {
            throw OrgStructureException.invalid("The file has " + rows.size() + " rows; at most " + limits.maxRows()
                    + " are allowed per import");
        }
        ImportReference reference = imports.loadReference(tenantId);
        List<ImportRow> validated = ImportRowValidator.validate(rows, reference);
        return imports.stage(tenantId, actor, file.documentId(), file.fileName(), validated);
    }

    @Override
    public ImportBatch batch(String tenantId, String batchId) {
        return imports.find(tenantId, batchId)
                .orElseThrow(() -> OrgStructureException.notFound("Import not found: " + batchId));
    }

    @Override
    public List<ImportBatch> recent(String tenantId, int limit) {
        return imports.recent(tenantId, limit);
    }

    @Override
    public List<ImportRow> errors(String tenantId, String batchId, int afterRowNo, int limit) {
        batch(tenantId, batchId);
        return imports.errors(batchId, afterRowNo, limit);
    }

    @Override
    public ImportBatch confirm(String tenantId, String batchId) {
        ImportBatch batch = batch(tenantId, batchId);
        if (batch.validRows() == 0) {
            throw OrgStructureException.invalid("There are no valid rows to import");
        }
        if (!imports.claimForApply(tenantId, batchId)) {
            throw OrgStructureException.conflict("Import is " + batch.status() + " and cannot be confirmed");
        }
        try {
            worker.execute(() -> run(tenantId, batchId));
        } catch (RejectedExecutionException e) {
            imports.finish(batchId, OrgStructureConstants.IMPORT_FAILED,
                    "The import queue is full; confirm again in a few minutes");
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
    void run(String tenantId, String batchId) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            ImportReference reference = imports.loadReference(tenantId);
            placeEmployees(tenantId, batchId, reference, today);
            applyManagers(tenantId, batchId, reference, today);
            imports.finish(batchId, OrgStructureConstants.IMPORT_APPLIED, null);
        } catch (RuntimeException e) {
            log.warn("Org import {} failed: {}", batchId, e.getMessage());
            imports.finish(batchId, OrgStructureConstants.IMPORT_FAILED, truncate(e.getMessage()));
        }
    }

    /** Pass 1: create or update each employee and place them in their department and position. */
    private void placeEmployees(String tenantId, String batchId, ImportReference reference, LocalDate today) {
        int after = 0;
        while (true) {
            List<StagedRow> chunk = imports.rows(batchId, OrgStructureConstants.IMPORT_ROW_VALID, after, limits.chunkSize());
            if (chunk.isEmpty()) {
                return;
            }
            tx.executeWithoutResult(status -> {
                int created = 0;
                int updated = 0;
                List<Integer> done = new ArrayList<>();
                for (StagedRow staged : chunk) {
                    ImportRowInput row = staged.input();
                    try {
                        Upserted upserted = upsertEmployee(tenantId, row);
                        if (upserted.created()) {
                            created++;
                        } else {
                            updated++;
                        }
                        place(tenantId, upserted.employee().id(), row, reference, today);
                        done.add(row.rowNo());
                    } catch (OrgStructureException e) {
                        throw OrgStructureException.invalid("Row " + row.rowNo() + ": " + e.getMessage());
                    }
                }
                imports.markRows(batchId, done, OrgStructureConstants.IMPORT_ROW_PLACED);
                imports.addCounts(batchId, created, updated);
            });
            after = chunk.get(chunk.size() - 1).input().rowNo();
        }
    }

    /** The saved employee and whether this import created them (as opposed to updating an existing one). */
    private record Upserted(Employee employee, boolean created) {
    }

    private Upserted upsertEmployee(String tenantId, ImportRowInput row) {
        Optional<Employee> existing = employees.findByCode(tenantId, row.employeeCode());
        LocalDate joined = row.joinedOn() == null ? null : LocalDate.parse(row.joinedOn());
        if (existing.isPresent()) {
            Employee current = existing.get();
            Employee merged = new Employee(current.id(), current.employeeCode(), row.fullName(),
                    row.email() != null ? row.email() : current.email(),
                    row.phone() != null ? row.phone() : current.phone(), current.status(), current.userId(),
                    joined != null ? joined : current.joinedOn());
            employees.update(tenantId, merged);
            return new Upserted(merged, false);
        }
        Employee created = employees.insert(tenantId, new Employee(null, row.employeeCode(), row.fullName(), row.email(),
                row.phone(), OrgStructureConstants.EMPLOYEE_ACTIVE, null, joined));
        return new Upserted(created, true);
    }

    private void place(String tenantId, String employeeId, ImportRowInput row, ImportReference reference, LocalDate today) {
        UnitRef unit = reference.unitsByCode().get(row.departmentCode());
        String positionId = reference.positionIdsByLadderAndName().get(unit.ladderId())
                .get(row.positionName().toLowerCase(Locale.ROOT));
        Optional<String[]> current = structure.currentPrimary(tenantId, employeeId);
        if (current.isPresent() && current.get()[0].equals(unit.id()) && current.get()[1].equals(positionId)) {
            return; // already exactly there: re-running an import must not churn assignment history
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("employeeId", employeeId);
        payload.put("unitId", unit.id());
        payload.put("positionId", positionId);
        payload.put("primary", true);
        structure.apply(tenantId, new Change(null, null, 0, ChangeType.ASSIGN, payload), today);
    }

    /** Pass 2: every employee now exists, so reporting lines can be set. */
    private void applyManagers(String tenantId, String batchId, ImportReference reference, LocalDate today) {
        int after = 0;
        while (true) {
            List<StagedRow> chunk = imports.rows(batchId, OrgStructureConstants.IMPORT_ROW_PLACED, after, limits.chunkSize());
            if (chunk.isEmpty()) {
                return;
            }
            tx.executeWithoutResult(status -> {
                List<Integer> done = new ArrayList<>();
                int heads = 0;
                for (StagedRow staged : chunk) {
                    ImportRowInput row = staged.input();
                    try {
                        setManager(tenantId, row, today);
                        if (setHead(tenantId, row, reference, today)) {
                            heads++;
                        }
                        done.add(row.rowNo());
                    } catch (OrgStructureException e) {
                        throw OrgStructureException.invalid("Row " + row.rowNo() + ": " + e.getMessage());
                    }
                }
                imports.markRows(batchId, done, OrgStructureConstants.IMPORT_ROW_APPLIED);
                if (heads > 0) {
                    imports.addHeadsSet(batchId, heads);
                }
            });
            after = chunk.get(chunk.size() - 1).input().rowNo();
        }
    }

    private void setManager(String tenantId, ImportRowInput row, LocalDate today) {
        if (row.managerCode() == null) {
            return; // a blank manager cell leaves the current reporting line alone
        }
        Employee employee = employees.findByCode(tenantId, row.employeeCode())
                .orElseThrow(() -> OrgStructureException.notFound("Employee not found: " + row.employeeCode()));
        Employee manager = employees.findByCode(tenantId, row.managerCode())
                .orElseThrow(() -> OrgStructureException.notFound("Manager not found: " + row.managerCode()));
        if (structure.currentManager(tenantId, employee.id()).filter(manager.id()::equals).isPresent()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("employeeId", employee.id());
        payload.put("managerEmployeeId", manager.id());
        structure.apply(tenantId, new Change(null, null, 0, ChangeType.SET_REPORTS_TO, payload), today);
    }

    /**
     * Makes the person the head of their department when the row says is_head=yes, through the same operations the org builder uses
     * ({@code UNIT_CLEAR_HEAD} for a different current head, then {@code UNIT_SET_HEAD}). They are already placed in the department
     * by pass 1. Returns whether a head was set; already being the head changes nothing.
     */
    private boolean setHead(String tenantId, ImportRowInput row, ImportReference reference, LocalDate today) {
        if (!ImportFlags.isYes(row.isHead())) {
            return false;
        }
        Employee employee = employees.findByCode(tenantId, row.employeeCode())
                .orElseThrow(() -> OrgStructureException.notFound("Employee not found: " + row.employeeCode()));
        String unitId = reference.unitsByCode().get(row.departmentCode()).id();
        Optional<String> current = structure.currentHead(tenantId, unitId);
        if (current.filter(employee.id()::equals).isPresent()) {
            return false;
        }
        if (current.isPresent()) {
            structure.apply(tenantId, new Change(null, null, 0, ChangeType.UNIT_CLEAR_HEAD, Map.of("unitId", unitId)), today);
        }
        structure.apply(tenantId, new Change(null, null, 0, ChangeType.UNIT_SET_HEAD,
                Map.of("unitId", unitId, "employeeId", employee.id())), today);
        return true;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
