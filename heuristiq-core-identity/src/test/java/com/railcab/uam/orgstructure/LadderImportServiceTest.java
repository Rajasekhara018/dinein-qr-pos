package com.heuristiq.uam.orgstructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.uam.orgstructure.application.LadderImportService;
import com.heuristiq.uam.orgstructure.application.OrgStructureChanged;
import com.heuristiq.uam.orgstructure.application.OrgStructureService;
import com.heuristiq.uam.orgstructure.domain.ChangeSetStore;
import com.heuristiq.uam.orgstructure.domain.ChangeType;
import com.heuristiq.uam.orgstructure.domain.EmployeeStore;
import com.heuristiq.uam.orgstructure.domain.ImportLimits;
import com.heuristiq.uam.orgstructure.domain.ImportStore;
import com.heuristiq.uam.orgstructure.domain.LadderFileSource;
import com.heuristiq.uam.orgstructure.domain.LadderImportAudit;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderBatch;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderFile;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRowInput;
import com.heuristiq.uam.orgstructure.domain.LadderImportStore;
import com.heuristiq.uam.orgstructure.domain.Models.Change;
import com.heuristiq.uam.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.OrgStructureStore;
import com.heuristiq.uam.orgstructure.domain.StructureSnapshotStore;

/**
 * The ladders and positions import end to end on H2 with the project's own migration scripts: a file is validated and staged without
 * touching the organization, only a confirmed batch is applied (through the org builder's ladder and position operations, ids made by
 * the server), a failed batch resumes, and one organization never sees another's import.
 */
class LadderImportServiceTest {
    private static final String TENANT = "tenant-1";
    private static final String OTHER = "tenant-2";
    private static final String ACTOR = "admin";
    private static final LocalDate TODAY = LocalDate.parse("2026-10-13");

    private OrgStructureStore structure;
    private OrgStructureService structureService;
    private LadderImportService service;
    private LadderImportStore store;
    private NamedParameterJdbcTemplate jdbc;
    private final AdjustableClock clock = new AdjustableClock();
    private final Map<String, List<LadderRowInput>> documents = new HashMap<>();
    private final List<Object> events = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();
    private final HeldExecutor executor = new HeldExecutor();
    private RuntimeException fileSourceFailure;

    private static final class AdjustableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T10:00:00Z");

        void advanceMinutes(long minutes) { now = now.plusSeconds(minutes * 60); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Runs submitted work at once, or keeps it until the test says so, or refuses it (a full queue). */
    private static final class HeldExecutor extends AbstractExecutorService {
        boolean hold;
        boolean reject;
        final List<Runnable> held = new ArrayList<>();

        @Override public void execute(Runnable command) {
            if (reject) {
                throw new RejectedExecutionException("full");
            }
            if (hold) {
                held.add(command);
            } else {
                command.run();
            }
        }
        void runHeld() { List<Runnable> now = new ArrayList<>(held); held.clear(); now.forEach(Runnable::run); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:ladderimport_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V8__org_import.sql"),
                new ClassPathResource("db/migration/V10__org_publish_snapshot.sql"),
                new ClassPathResource("db/migration/V21__org_ladder_import_and_heads.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        DataSourceTransactionManager tx = new DataSourceTransactionManager(dataSource);
        structure = (OrgStructureStore) construct("JdbcOrgStructureStore", jdbc, clock);
        store = (LadderImportStore) construct("JdbcLadderImportStore", jdbc, clock);
        ChangeSetStore changes = (ChangeSetStore) construct("JdbcChangeSetStore", jdbc, new ObjectMapper(), clock);
        EmployeeStore employees = (EmployeeStore) construct("JdbcEmployeeStore", jdbc, clock);
        ImportStore employeeImports = (ImportStore) construct("JdbcImportStore", jdbc, clock);
        StructureSnapshotStore snapshots = (StructureSnapshotStore) construct("JdbcStructureSnapshotStore", jdbc, clock);
        structureService = new OrgStructureService(structure, changes, employees, snapshots, employeeImports, tx, clock);
        structureService.setLadderImports(store);
        LadderFileSource files = (documentId, callerId) -> {
            if (fileSourceFailure != null) {
                throw fileSourceFailure;
            }
            List<LadderRowInput> rows = documents.get(documentId);
            if (rows == null) {
                throw OrgStructureException.notFound("The uploaded file was not found: " + documentId);
            }
            return new LadderFile(documentId, documentId + ".csv", rows);
        };
        LadderImportAudit audit = (tenant, actor, batch, success, file, total, valid, created, updated, laddersCreated, message) ->
                audits.add(tenant + "|" + actor + "|" + success + "|" + total + "|" + valid + "|" + created + "|" + updated + "|"
                        + laddersCreated + "|" + message);
        service = new LadderImportService(store, files, structure, new ImportLimits(1000, 2), executor, tx, clock, events::add, audit);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Object construct(String simpleName, Object... args) throws Exception {
        var constructor = Class.forName("com.heuristiq.uam.orgstructure.adapter.out." + simpleName).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static LadderRowInput row(int no, String ladder, String position, String rank, String managerial) {
        return new LadderRowInput(no, ladder, position, rank, managerial);
    }

    private LadderBatch upload(String tenant, List<LadderRowInput> rows) {
        String documentId = "doc-" + documents.size();
        documents.put(documentId, rows);
        return service.upload(tenant, ACTOR, documentId);
    }

    private LadderBatch fresh(String tenant, String batchId) {
        return service.batch(tenant, batchId);
    }

    private String ladder(String tenant, String name) {
        String id = UUID.randomUUID().toString();
        structure.apply(tenant, new Change(null, null, 0, ChangeType.LADDER_CREATE, Map.of("id", id, "name", name)), TODAY);
        return id;
    }

    private void position(String tenant, String ladderId, String name, int rank, boolean managerial) {
        structure.apply(tenant, new Change(null, null, 0, ChangeType.POSITION_UPSERT,
                Map.of("id", UUID.randomUUID().toString(), "ladderId", ladderId, "name", name, "rank", rank, "managerial", managerial)), TODAY);
    }

    private int count(String sql, Object... named) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < named.length; i += 2) {
            params.put((String) named[i], named[i + 1]);
        }
        Integer n = jdbc.queryForObject(sql, params, Integer.class);
        return n == null ? 0 : n;
    }

    private int ladders(String tenant) {
        return count("select count(*) from org_ladder where tenant_id = :t", "t", tenant);
    }

    private int positions(String tenant) {
        return count("select count(*) from org_position where tenant_id = :t", "t", tenant);
    }

    private ExistingPositionView positionOf(String tenant, String ladderName, String positionName) {
        return jdbc.query("""
                select p.id, p.rank_no, p.managerial from org_position p join org_ladder l on l.id = p.ladder_id
                where p.tenant_id = :t and l.name = :l and p.name = :p""", Map.of("t", tenant, "l", ladderName, "p", positionName),
                (rs, i) -> new ExistingPositionView(rs.getString("id"), rs.getInt("rank_no"), rs.getBoolean("managerial"))).stream()
                .findFirst().orElse(null);
    }

    private record ExistingPositionView(String id, int rank, boolean managerial) {
    }

    // ---- upload: validate and stage, change nothing ---------------------------------------------------

    @Test
    void uploadValidatesEveryRowAndChangesNothingLive() {
        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", null), row(2, "Tech", "Engineer", "4", null),
                row(3, "Tech", "Lead", "x", "yes"), row(4, "Sales", "Executive", "2", "no")));

        assertThat(batch.status()).isEqualTo(OrgStructureConstants.IMPORT_VALIDATED);
        assertThat(batch.totalRows()).isEqualTo(4);
        assertThat(batch.validRows()).isEqualTo(2);
        assertThat(batch.errorRows()).isEqualTo(2);
        assertThat(ladders(TENANT)).isZero();
        assertThat(positions(TENANT)).isZero();
        assertThat(service.errors(TENANT, batch.id(), 0, 50)).extracting(r -> r.errors().get(0))
                .containsExactly("DUPLICATE_ROW:1", "RANK_INVALID");
        assertThat(events).isEmpty();
    }

    @Test
    void aFileWithNoRowsOrTooManyRowsIsRefused() {
        assertThatThrownBy(() -> upload(TENANT, List.of())).isInstanceOf(OrgStructureException.class).hasMessage("The file has no data rows");
        List<LadderRowInput> tooMany = new ArrayList<>();
        for (int i = 1; i <= 1001; i++) {
            tooMany.add(row(i, "L", "P" + i, "1", null));
        }
        assertThatThrownBy(() -> upload(TENANT, tooMany)).isInstanceOf(OrgStructureException.class).hasMessageContaining("at most 1000");
    }

    @Test
    void aMissingDocumentIsNotFoundAndADocumentStoreOutageIsNotAnAnswerAboutTheFile() {
        assertThatThrownBy(() -> service.upload(TENANT, ACTOR, "nope")).isInstanceOf(OrgStructureException.class)
                .extracting(e -> ((OrgStructureException) e).kind()).isEqualTo(OrgStructureException.Kind.NOT_FOUND);
        fileSourceFailure = new DownstreamUnavailableException("dms-service", DownstreamUnavailableException.Reason.CIRCUIT_OPEN, null);
        assertThatThrownBy(() -> service.upload(TENANT, ACTOR, "doc-0")).isInstanceOf(DownstreamUnavailableException.class);
    }

    // ---- confirm: apply -------------------------------------------------------------------------------

    @Test
    void confirmCreatesTheMissingLaddersAndPositionsWithServerMadeIds() {
        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", null), row(2, "Tech", "Lead", "6", "yes"),
                row(3, "Sales", "Executive", "2", "no"), row(4, "tech", "Principal", "9", "TRUE"), row(5, "Tech", "Bad", "0", null)));

        service.confirm(TENANT, ACTOR, batch.id());

        LadderBatch done = fresh(TENANT, batch.id());
        assertThat(done.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(done.createdCount()).isEqualTo(4);
        assertThat(done.updatedCount()).isZero();
        assertThat(done.laddersCreated()).isEqualTo(2);
        assertThat(done.appliedAt()).isNotNull();
        assertThat(ladders(TENANT)).isEqualTo(2);
        assertThat(positions(TENANT)).isEqualTo(4);
        assertThat(positionOf(TENANT, "Tech", "Lead").managerial()).isTrue();
        assertThat(positionOf(TENANT, "Tech", "Engineer")).satisfies(p -> {
            assertThat(p.rank()).isEqualTo(3);
            assertThat(p.managerial()).isFalse();
            assertThat(UUID.fromString(p.id())).isNotNull();
        });
        assertThat(positionOf(TENANT, "Tech", "Principal").rank()).isEqualTo(9);
        assertThat(positionOf(TENANT, "Tech", "Bad")).as("a rejected row is skipped").isNull();
        assertThat(events).containsExactly(new OrgStructureChanged(TENANT));
        assertThat(audits).containsExactly(TENANT + "|admin|true|5|4|4|0|2|null");
        assertThat(count("select count(*) from org_ladder_import_row where status = 'VALID'")).isZero();
    }

    @Test
    void aNamedLadderThatExistsIsReusedIgnoringCaseAndNoDuplicateLadderIsMade() {
        String tech = ladder(TENANT, "Tech Ladder");
        position(TENANT, tech, "Engineer", 3, false);

        LadderBatch batch = upload(TENANT, List.of(row(1, " tech LADDER ", "Lead", "6", "yes")));
        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(ladders(TENANT)).isEqualTo(1);
        assertThat(positions(TENANT)).isEqualTo(2);
        assertThat(fresh(TENANT, batch.id()).laddersCreated()).isZero();
        assertThat(positionOf(TENANT, "Tech Ladder", "Lead")).isNotNull();
    }

    @Test
    void anExistingPositionIsUpdatedInPlaceAndKeepsItsIdAndAnUnchangedOneCountsNothing() {
        String tech = ladder(TENANT, "Tech");
        position(TENANT, tech, "Engineer", 3, false);
        position(TENANT, tech, "Lead", 6, false);
        String leadId = positionOf(TENANT, "Tech", "Lead").id();

        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", "no"), row(2, "Tech", "lead", "7", "yes")));
        service.confirm(TENANT, ACTOR, batch.id());

        LadderBatch done = fresh(TENANT, batch.id());
        assertThat(done.createdCount()).isZero();
        assertThat(done.updatedCount()).isEqualTo(1);
        assertThat(positions(TENANT)).isEqualTo(2);
        assertThat(count("select count(*) from org_position where id = :id", "id", leadId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select rank_no from org_position where id = :id", Map.of("id", leadId), Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select name from org_position where id = :id", Map.of("id", leadId), String.class)).isEqualTo("lead");
    }

    @Test
    void runningTheSameFileAgainChangesNothingAndPublishesNoEvent() {
        List<LadderRowInput> file = List.of(row(1, "Tech", "Engineer", "3", null), row(2, "Tech", "Lead", "6", "yes"));
        service.confirm(TENANT, ACTOR, upload(TENANT, file).id());
        events.clear();

        LadderBatch second = upload(TENANT, file);
        service.confirm(TENANT, ACTOR, second.id());

        LadderBatch done = fresh(TENANT, second.id());
        assertThat(done.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(done.createdCount()).isZero();
        assertThat(done.updatedCount()).isZero();
        assertThat(done.laddersCreated()).isZero();
        assertThat(ladders(TENANT)).isEqualTo(1);
        assertThat(positions(TENANT)).isEqualTo(2);
        assertThat(events).isEmpty();
    }

    @Test
    void aRankAnExistingPositionHoldsIsRejectedAtUploadSoNothingFailsHalfWay() {
        String tech = ladder(TENANT, "Tech");
        position(TENANT, tech, "Engineer", 3, false);

        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Architect", "3", null), row(2, "Tech", "Lead", "6", null)));

        assertThat(batch.validRows()).isEqualTo(1);
        assertThat(service.errors(TENANT, batch.id(), 0, 50).get(0).errors()).containsExactly("RANK_TAKEN:Engineer");
    }

    @Test
    void theErrorsAreKeysetPagedAndCapped() {
        List<LadderRowInput> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            rows.add(row(i, "L", "P" + i, "x", null));
        }
        LadderBatch batch = upload(TENANT, rows);

        assertThat(service.errors(TENANT, batch.id(), 0, 2)).extracting(r -> r.input().rowNo()).containsExactly(1, 2);
        assertThat(service.errors(TENANT, batch.id(), 2, 2)).extracting(r -> r.input().rowNo()).containsExactly(3, 4);
        assertThat(service.errors(TENANT, batch.id(), 4, 100)).extracting(r -> r.input().rowNo()).containsExactly(5);
    }

    // ---- life cycle ---------------------------------------------------------------------------------------

    @Test
    void aCancelledImportChangesNothingAndCannotBeConfirmedOrCancelledAgain() {
        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", null)));

        service.cancel(TENANT, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_CANCELLED);
        assertThat(ladders(TENANT)).isZero();
        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class).hasMessageContaining("cannot be confirmed");
        assertThatThrownBy(() -> service.cancel(TENANT, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("Only a validated import can be cancelled");
    }

    @Test
    void aSecondConfirmWhileTheFirstIsApplyingIsRefusedAndTheWorkRunsOnlyOnce() {
        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", null)));
        executor.hold = true;

        LadderBatch first = service.confirm(TENANT, ACTOR, batch.id());

        assertThat(first.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLYING);
        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class).hasMessageContaining("APPLYING");
        assertThat(executor.held).hasSize(1);
        executor.runHeld();
        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(fresh(TENANT, batch.id()).createdCount()).isEqualTo(1);
        assertThat(positions(TENANT)).isEqualTo(1);
    }

    @Test
    void aFullQueueEndsTheBatchFailedSoItCanBeConfirmedAgainLater() {
        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", null)));
        executor.reject = true;

        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("Another import is running");
        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        executor.reject = false;

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(positions(TENANT)).isEqualTo(1);
    }

    @Test
    void anImportWithNoValidRowCannotBeConfirmed() {
        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "x", null)));

        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessage("There are no valid rows to import");
    }

    @Test
    void aFailureWhileApplyingEndsTheBatchFailedAndConfirmingAgainResumesWhereItStopped() {
        // rows 1-2 form the first chunk (chunk size 2); row 3 collides with a rank taken after the file was checked
        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", null), row(2, "Tech", "Lead", "6", null),
                row(3, "Tech", "Architect", "8", null), row(4, "Tech", "Principal", "9", null)));
        String tech = null;
        service.confirm(TENANT, ACTOR, upload(TENANT, List.of(row(1, "Tech", "Seed", "1", null))).id());
        tech = jdbc.queryForObject("select id from org_ladder where name = 'Tech'", Map.of(), String.class);
        // meanwhile somebody takes rank 8, so row 3 can no longer be applied
        position(TENANT, tech, "Intruder", 8, false);

        service.confirm(TENANT, ACTOR, batch.id());

        LadderBatch failed = fresh(TENANT, batch.id());
        assertThat(failed.status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        assertThat(failed.message()).startsWith("Row 3:");
        assertThat(failed.createdCount()).as("the first chunk was committed before the failing one").isEqualTo(2);
        assertThat(audits.get(1)).contains("|false|").contains("Row 3");
        // fix the cause and confirm again: only the unfinished rows are done
        jdbc.update("delete from org_position where name = 'Intruder'", Map.of());

        service.confirm(TENANT, ACTOR, batch.id());

        LadderBatch resumed = fresh(TENANT, batch.id());
        assertThat(resumed.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(resumed.createdCount()).isEqualTo(4);
        assertThat(positions(TENANT)).isEqualTo(5);
    }

    // ---- organizations ---------------------------------------------------------------------------------

    @Test
    void oneOrganizationCannotSeeConfirmCancelOrListAnotherOnesImport() {
        LadderBatch mine = upload(TENANT, List.of(row(1, "Tech", "Engineer", "x", null), row(2, "Tech", "Lead", "6", null)));

        assertThatThrownBy(() -> service.batch(OTHER, mine.id())).isInstanceOf(OrgStructureException.class)
                .extracting(e -> ((OrgStructureException) e).kind()).isEqualTo(OrgStructureException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> service.errors(OTHER, mine.id(), 0, 50)).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> service.confirm(OTHER, ACTOR, mine.id())).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> service.cancel(OTHER, mine.id())).isInstanceOf(OrgStructureException.class);
        assertThat(service.recent(OTHER, 50)).isEmpty();
        assertThat(store.errors(OTHER, mine.id(), 0, 50)).as("the store itself filters on the organization").isEmpty();
        assertThat(store.claimForApply(OTHER, mine.id())).isFalse();
        assertThat(store.cancel(OTHER, mine.id())).isFalse();
        assertThat(service.recent(TENANT, 50)).extracting(LadderBatch::id).containsExactly(mine.id());
        assertThat(fresh(TENANT, mine.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_VALIDATED);
    }

    @Test
    void aFileIsCheckedAgainstAndAppliedToTheCallersOrganizationOnly() {
        String theirs = ladder(OTHER, "Tech");
        position(OTHER, theirs, "Engineer", 3, false);

        LadderBatch batch = upload(TENANT, List.of(row(1, "Tech", "Architect", "3", null)));

        assertThat(batch.validRows()).as("rank 3 is only taken in the other organization").isEqualTo(1);
        service.confirm(TENANT, ACTOR, batch.id());
        assertThat(ladders(TENANT)).isEqualTo(1);
        assertThat(ladders(OTHER)).isEqualTo(1);
        assertThat(positions(TENANT)).isEqualTo(1);
        assertThat(positions(OTHER)).as("the other organization is untouched").isEqualTo(1);
        assertThat(positionOf(OTHER, "Tech", "Architect")).isNull();
        assertThat(store.findLadderId(TENANT, "tech")).isPresent().isNotEqualTo(store.findLadderId(OTHER, "tech"));
    }

    // ---- rollback guard -----------------------------------------------------------------------------------

    @Test
    void rollingBackAPublishIsRefusedOnceALaddersImportWasAppliedAfterIt() {
        ChangeSet draft = structureService.openDraft(TENANT, ACTOR, null);
        structureService.addChange(TENANT, draft.id(), ChangeType.UNIT_CREATE, Map.of("code", "ROOT", "name", "Company"));
        assertThat(structureService.publish(TENANT, draft.id(), ACTOR).applied()).isTrue();
        clock.advanceMinutes(5);
        service.confirm(TENANT, ACTOR, upload(TENANT, List.of(row(1, "Tech", "Engineer", "3", null))).id());

        assertThatThrownBy(() -> structureService.rollbackLatest(TENANT, ACTOR)).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("A ladders import was applied after this publish");
        assertThat(positions(TENANT)).isEqualTo(1);
    }

    @Test
    void rollingBackIsStillAllowedWhenNoLaddersImportCameAfterThePublish() {
        ChangeSet draft = structureService.openDraft(TENANT, ACTOR, null);
        structureService.addChange(TENANT, draft.id(), ChangeType.UNIT_CREATE, Map.of("code", "ROOT", "name", "Company"));
        structureService.publish(TENANT, draft.id(), ACTOR);

        structureService.rollbackLatest(TENANT, ACTOR);

        assertThat(count("select count(*) from org_unit")).isZero();
    }
}
