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
import com.heuristiq.identity.orgstructure.domain.ImportFlags;
import com.heuristiq.identity.orgstructure.domain.ImportLimits;
import com.heuristiq.identity.orgstructure.domain.LadderFileSource;
import com.heuristiq.identity.orgstructure.domain.LadderImportAudit;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.ExistingPosition;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderBatch;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderFile;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderReference;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRow;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRowInput;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.StagedLadderRow;
import com.heuristiq.identity.orgstructure.domain.LadderImportStore;
import com.heuristiq.identity.orgstructure.domain.LadderRowValidator;
import com.heuristiq.identity.orgstructure.domain.Models.Change;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;

/**
 * Bulk ladders and positions import. An upload is validated in full and staged without touching the live organization; only a confirmed
 * batch is applied, in short transactions of {@code chunkSize} rows on the same bounded background worker the other imports use, so
 * imports of one organization never run at the same time. Ladders and positions are created and changed through the same
 * {@link OrgStructureStore#apply} operations the org builder's publish uses ({@code LADDER_CREATE}, {@code POSITION_UPSERT}); the
 * ids are generated here, never read from the file. Applying is idempotent by name, so a failed batch can be confirmed again and carries
 * on where it stopped.
 */
@Service
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class LadderImportService implements LadderImportUseCase {
    private static final Logger log = LoggerFactory.getLogger(LadderImportService.class);

    private final LadderImportStore imports;
    private final LadderFileSource files;
    private final OrgStructureStore structure;
    private final ImportLimits limits;
    private final ExecutorService worker;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final LadderImportAudit audit;

    public LadderImportService(LadderImportStore imports, LadderFileSource files, OrgStructureStore structure, ImportLimits limits,
                               @Qualifier("orgImportExecutor") ExecutorService worker, PlatformTransactionManager transactionManager,
                               Clock clock, ApplicationEventPublisher events, LadderImportAudit audit) {
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
    public LadderBatch upload(String tenantId, String actor, String documentId) {
        LadderFile file = files.fetchLadders(documentId, actor);
        List<LadderRowInput> rows = file.rows();
        if (rows.isEmpty()) {
            throw OrgStructureException.invalid("The file has no data rows");
        }
        if (rows.size() > limits.maxRows()) {
            throw OrgStructureException.invalid("The file has " + rows.size() + " rows; at most " + limits.maxRows()
                    + " are allowed per import");
        }
        LadderReference reference = imports.loadReference(tenantId);
        List<LadderRow> validated = LadderRowValidator.validate(rows, reference);
        return imports.stage(tenantId, actor, file.documentId(), file.fileName(), validated);
    }

    @Override
    public LadderBatch batch(String tenantId, String batchId) {
        return imports.find(tenantId, batchId)
                .orElseThrow(() -> OrgStructureException.notFound("Import not found: " + batchId));
    }

    @Override
    public List<LadderBatch> recent(String tenantId, int limit) {
        return imports.recent(tenantId, limit);
    }

    @Override
    public List<LadderRow> errors(String tenantId, String batchId, int afterRowNo, int limit) {
        batch(tenantId, batchId);
        return imports.errors(tenantId, batchId, afterRowNo, limit);
    }

    @Override
    public LadderBatch confirm(String tenantId, String actor, String batchId) {
        LadderBatch batch = batch(tenantId, batchId);
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
            int after = 0;
            while (true) {
                List<StagedLadderRow> chunk = imports.rows(batchId, OrgStructureConstants.IMPORT_ROW_VALID, after, limits.chunkSize());
                if (chunk.isEmpty()) {
                    break;
                }
                tx.executeWithoutResult(status -> applyChunk(tenantId, batchId, chunk, today));
                after = chunk.get(chunk.size() - 1).input().rowNo();
            }
            imports.finish(batchId, OrgStructureConstants.IMPORT_APPLIED, null);
            success = true;
        } catch (RuntimeException e) {
            log.warn("Ladders import {} failed: {}", batchId, e.getMessage());
            message = truncate(e.getMessage());
            imports.finish(batchId, OrgStructureConstants.IMPORT_FAILED, message);
        }
        afterRun(tenantId, actor, batchId, success, message);
    }

    /** Tells the rest of the platform the structure changed (when anything did) and writes the audit entry. */
    private void afterRun(String tenantId, String actor, String batchId, boolean success, String message) {
        LadderBatch ended = imports.find(tenantId, batchId).orElse(null);
        if (ended == null) {
            return;
        }
        if (ended.createdCount() + ended.updatedCount() + ended.laddersCreated() > 0) {
            events.publishEvent(new OrgStructureChanged(tenantId));
        }
        audit.record(tenantId, actor, batchId, success, ended.fileName(), ended.totalRows(), ended.validRows(), ended.createdCount(),
                ended.updatedCount(), ended.laddersCreated(), message);
    }

    private void applyChunk(String tenantId, String batchId, List<StagedLadderRow> chunk, LocalDate today) {
        int created = 0;
        int updated = 0;
        int laddersCreated = 0;
        List<Integer> done = new ArrayList<>();
        for (StagedLadderRow staged : chunk) {
            LadderRowInput row = staged.input();
            try {
                Optional<String> existingLadder = imports.findLadderId(tenantId, row.ladder());
                String ladderId = existingLadder.orElseGet(() -> createLadder(tenantId, row.ladder(), today));
                if (existingLadder.isEmpty()) {
                    laddersCreated++;
                }
                switch (applyPosition(tenantId, ladderId, row, today)) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> { }
                }
                done.add(row.rowNo());
            } catch (OrgStructureException e) {
                throw OrgStructureException.invalid("Row " + row.rowNo() + ": " + e.getMessage());
            }
        }
        if (!done.isEmpty()) {
            imports.markRows(batchId, done, OrgStructureConstants.IMPORT_ROW_APPLIED);
            imports.addCounts(batchId, created, updated, laddersCreated);
        }
    }

    private String createLadder(String tenantId, String name, LocalDate today) {
        String id = UUID.randomUUID().toString();
        structure.apply(tenantId, new Change(null, null, 0, ChangeType.LADDER_CREATE, Map.of("id", id, "name", name)), today);
        return id;
    }

    private enum Outcome { CREATED, UPDATED, UNCHANGED }

    /** Creates the position, or changes the existing one (matched by name) when its name's case, rank or managerial flag differ. */
    private Outcome applyPosition(String tenantId, String ladderId, LadderRowInput row, LocalDate today) {
        int rank = LadderRowValidator.parseRank(row.rank());
        boolean managerial = ImportFlags.isYes(row.managerial());
        Optional<ExistingPosition> existing = imports.findPosition(tenantId, ladderId, row.position());
        if (existing.isPresent() && existing.get().name().equals(row.position()) && existing.get().rank() == rank
                && existing.get().managerial() == managerial) {
            return Outcome.UNCHANGED;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", existing.map(ExistingPosition::id).orElseGet(() -> UUID.randomUUID().toString()));
        payload.put("ladderId", ladderId);
        payload.put("name", row.position());
        payload.put("rank", rank);
        payload.put("managerial", managerial);
        structure.apply(tenantId, new Change(null, null, 0, ChangeType.POSITION_UPSERT, payload), today);
        return existing.isPresent() ? Outcome.UPDATED : Outcome.CREATED;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
