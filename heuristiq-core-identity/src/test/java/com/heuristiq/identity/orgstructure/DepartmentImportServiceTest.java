package com.heuristiq.identity.orgstructure;

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
import com.heuristiq.identity.orgstructure.application.DepartmentImportService;
import com.heuristiq.identity.orgstructure.application.OrgStructureChanged;
import com.heuristiq.identity.orgstructure.application.OrgStructureService;
import com.heuristiq.identity.orgstructure.domain.ChangeSetStore;
import com.heuristiq.identity.orgstructure.domain.ChangeType;
import com.heuristiq.identity.orgstructure.domain.DepartmentFileSource;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportAudit;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentFile;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRowInput;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.UnitState;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportStore;
import com.heuristiq.identity.orgstructure.domain.EmployeeStore;
import com.heuristiq.identity.orgstructure.domain.ImportLimits;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentBatch;
import com.heuristiq.identity.orgstructure.domain.ImportStore;
import com.heuristiq.identity.orgstructure.domain.Models.Change;
import com.heuristiq.identity.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;
import com.heuristiq.identity.orgstructure.domain.StructureSnapshotStore;

/**
 * The department import end to end on H2 with the project's own migration scripts: a file is validated and staged without touching the
 * organization, only a confirmed batch is applied (parents before children, through the org builder's own unit operations), a failed
 * batch resumes, and one organization never sees another's import.
 */
class DepartmentImportServiceTest {
    private static final String TENANT = "tenant-1";
    private static final String OTHER = "tenant-2";
    private static final String ACTOR = "admin";
    private static final LocalDate TODAY = LocalDate.parse("2026-10-13");

    private OrgStructureStore structure;
    private OrgStructureService structureService;
    private DepartmentImportService service;
    private DepartmentImportStore store;
    private NamedParameterJdbcTemplate jdbc;
    private DataSourceTransactionManager tx;
    private final AdjustableClock clock = new AdjustableClock();
    private ImportStore employeeImports;
    private final Map<String, List<DepartmentRowInput>> documents = new HashMap<>();
    private final List<Object> events = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();
    private final HeldExecutor executor = new HeldExecutor();
    private RuntimeException fileSourceFailure;

    /** A clock the test can move forward, so "after the publish" is really after it. */
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
        String url = "jdbc:h2:mem:deptimport_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V8__org_import.sql"),
                new ClassPathResource("db/migration/V10__org_publish_snapshot.sql"),
                new ClassPathResource("db/migration/V19__org_department_import.sql"),
                new ClassPathResource("db/migration/V20__org_department_import_ladder.sql"),
                new ClassPathResource("db/migration/V21__org_ladder_import_and_heads.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        tx = new DataSourceTransactionManager(dataSource);
        structure = (OrgStructureStore) construct("JdbcOrgStructureStore", jdbc, clock);
        store = (DepartmentImportStore) construct("JdbcDepartmentImportStore", jdbc, clock);
        ChangeSetStore changes = (ChangeSetStore) construct("JdbcChangeSetStore", jdbc, new ObjectMapper(), clock);
        EmployeeStore employees = (EmployeeStore) construct("JdbcEmployeeStore", jdbc, clock);
        employeeImports = (ImportStore) construct("JdbcImportStore", jdbc, clock);
        StructureSnapshotStore snapshots = (StructureSnapshotStore) construct("JdbcStructureSnapshotStore", jdbc, clock);
        structureService = new OrgStructureService(structure, changes, employees, snapshots, employeeImports, tx, clock);
        structureService.setDepartmentImports(store);
        DepartmentFileSource files = (documentId, callerId) -> {
            if (fileSourceFailure != null) {
                throw fileSourceFailure;
            }
            List<DepartmentRowInput> rows = documents.get(documentId);
            if (rows == null) {
                throw OrgStructureException.notFound("The uploaded file was not found: " + documentId);
            }
            return new DepartmentFile(documentId, documentId + ".csv", rows);
        };
        DepartmentImportAudit audit = (tenant, actor, batch, success, file, total, valid, created, updated, message) ->
                audits.add(tenant + "|" + actor + "|" + success + "|" + total + "|" + valid + "|" + created + "|" + updated + "|" + message);
        service = new DepartmentImportService(store, files, structure, new ImportLimits(1000, 2), executor, tx, clock, events::add, audit);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void unit(String tenant, String code, String name, String parentCode, String ladderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("code", code);
        payload.put("name", name);
        if (parentCode != null) {
            payload.put("parentId", id(tenant, parentCode));
        }
        if (ladderId != null) {
            payload.put("ladderId", ladderId);
        }
        structure.apply(tenant, new Change(null, null, 0, ChangeType.UNIT_CREATE, payload), TODAY);
    }

    private String ladder(String tenant, String name) {
        String id = UUID.randomUUID().toString();
        structure.apply(tenant, new Change(null, null, 0, ChangeType.LADDER_CREATE, Map.of("id", id, "name", name)), TODAY);
        return id;
    }

    private String id(String tenant, String code) {
        return store.findUnit(tenant, code).orElseThrow().id();
    }

    private UnitState state(String tenant, String code) {
        return store.findUnit(tenant, code).orElseThrow();
    }

    private static DepartmentRowInput row(int no, String code, String name, String parent) {
        return new DepartmentRowInput(no, code, name, parent, null);
    }

    private DepartmentBatch upload(String tenant, List<DepartmentRowInput> rows) {
        String documentId = "doc-" + documents.size();
        documents.put(documentId, rows);
        return service.upload(tenant, ACTOR, documentId);
    }

    private int count(String sql, Object... named) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < named.length; i += 2) {
            params.put((String) named[i], named[i + 1]);
        }
        Integer n = jdbc.queryForObject(sql, params, Integer.class);
        return n == null ? 0 : n;
    }

    private int units() {
        return count("select count(*) from org_unit");
    }

    private int closureBelow(String tenant, String code) {
        return count("select count(*) from org_unit_closure where ancestor_id = :id", "id", id(tenant, code));
    }

    private DepartmentBatch fresh(String tenant, String batchId) {
        return service.batch(tenant, batchId);
    }

    private static Object construct(String simpleName, Object... args) throws Exception {
        var constructor = Class.forName("com.heuristiq.identity.orgstructure.adapter.out." + simpleName).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static DepartmentRowInput ladderRow(int no, String code, String name, String parent, String ladder) {
        return new DepartmentRowInput(no, code, name, parent, null, ladder);
    }

    // ---- the ladder column -----------------------------------------------------------------------------

    @Test
    void aNamedLadderIsGivenToNewDepartmentsAndAChildWithoutOneInheritsItWhateverTheRowOrder() {
        String tech = ladder(TENANT, "Tech Ladder");
        DepartmentBatch batch = upload(TENANT, List.of(ladderRow(1, "KID", "Kid", "ENG", null), ladderRow(2, "ENG", "Engineering", null, " tech LADDER "),
                ladderRow(3, "GRANDKID", "Grandkid", "KID", null)));

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(state(TENANT, "ENG").ladderId()).isEqualTo(tech);
        assertThat(state(TENANT, "KID").ladderId()).isEqualTo(tech);
        assertThat(state(TENANT, "GRANDKID").ladderId()).isEqualTo(tech);
        assertThat(fresh(TENANT, batch.id()).withoutLadder()).isZero();
    }

    @Test
    void aLadderNamedOnAChildOverridesTheOneItWouldInherit() {
        String tech = ladder(TENANT, "Tech");
        String sales = ladder(TENANT, "Sales");
        unit(TENANT, "ROOT", "Company", null, tech);

        service.confirm(TENANT, ACTOR, upload(TENANT, List.of(ladderRow(1, "S", "Sales", "ROOT", "Sales"), ladderRow(2, "T", "Team", "ROOT", null))).id());

        assertThat(state(TENANT, "S").ladderId()).isEqualTo(sales);
        assertThat(state(TENANT, "T").ladderId()).isEqualTo(tech);
    }

    @Test
    void anUnknownLadderRejectsTheRowAndNothingFromItIsCreated() {
        ladder(TENANT, "Tech");
        DepartmentBatch batch = upload(TENANT, List.of(ladderRow(1, "A", "A", null, "Nope"), ladderRow(2, "B", "B", null, "Tech")));

        assertThat(batch.errorRows()).isEqualTo(1);
        assertThat(service.errors(TENANT, batch.id(), 0, 10)).singleElement().satisfies(r -> {
            assertThat(r.errors()).containsExactly("LADDER_UNKNOWN:Nope");
            assertThat(r.input().ladder()).isEqualTo("Nope");
        });
        service.confirm(TENANT, ACTOR, batch.id());
        assertThat(store.findUnit(TENANT, "A")).isEmpty();
        assertThat(store.findUnit(TENANT, "B")).isPresent();
    }

    @Test
    void aLadderOfAnotherOrganizationIsUnknown() {
        ladder(OTHER, "Theirs");

        DepartmentBatch batch = upload(TENANT, List.of(ladderRow(1, "A", "A", null, "Theirs")));

        assertThat(batch.errorRows()).isEqualTo(1);
    }

    @Test
    void newDepartmentsWithNoLadderAreCreatedAndCountedOnTheBatch() {
        String tech = ladder(TENANT, "Tech");
        unit(TENANT, "PLAIN", "Plain", null, null);
        unit(TENANT, "EXT", "Existing tech", null, tech);

        DepartmentBatch batch = upload(TENANT, List.of(ladderRow(1, "ROOT", "Root", null, null), ladderRow(2, "KID", "Kid", "ROOT", null),
                ladderRow(3, "UNDER_PLAIN", "Under plain", "PLAIN", null), ladderRow(4, "UNDER_EXT", "Under ext", "EXT", null)));

        assertThat(batch.validRows()).isEqualTo(4);
        assertThat(batch.withoutLadder()).isEqualTo(3);
        service.confirm(TENANT, ACTOR, batch.id());
        assertThat(state(TENANT, "ROOT").ladderId()).isNull();
        assertThat(state(TENANT, "UNDER_EXT").ladderId()).isEqualTo(tech);
        assertThat(fresh(TENANT, batch.id()).withoutLadder()).isEqualTo(3);
        assertThat(fresh(TENANT, batch.id()).createdCount()).isEqualTo(4);
    }

    @Test
    void aBlankLadderOnAnExistingDepartmentLeavesItsLadderAndCountsNothing() {
        String tech = ladder(TENANT, "Tech");
        unit(TENANT, "ENG", "Engineering", null, tech);
        unit(TENANT, "PLAIN", "Plain", null, null);

        DepartmentBatch batch = upload(TENANT, List.of(ladderRow(1, "ENG", "Engineering", null, null), ladderRow(2, "PLAIN", "Plain", null, null)));
        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(state(TENANT, "ENG").ladderId()).isEqualTo(tech);
        assertThat(state(TENANT, "PLAIN").ladderId()).isNull();
        assertThat(fresh(TENANT, batch.id()).updatedCount()).isZero();
        assertThat(fresh(TENANT, batch.id()).withoutLadder()).isZero();
    }

    @Test
    void aNamedLadderChangesTheLadderOfAnExistingDepartmentOnlyWhenItDiffers() {
        String tech = ladder(TENANT, "Tech");
        String sales = ladder(TENANT, "Sales");
        unit(TENANT, "ENG", "Engineering", null, tech);
        unit(TENANT, "MKT", "Marketing", null, tech);
        unit(TENANT, "NEW", "No ladder yet", null, null);

        DepartmentBatch batch = upload(TENANT, List.of(ladderRow(1, "ENG", "Engineering", null, "tech"),
                ladderRow(2, "MKT", "Marketing", null, "Sales"), ladderRow(3, "NEW", "No ladder yet", null, "Tech")));
        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(state(TENANT, "ENG").ladderId()).isEqualTo(tech);
        assertThat(state(TENANT, "MKT").ladderId()).isEqualTo(sales);
        assertThat(state(TENANT, "NEW").ladderId()).isEqualTo(tech);
        assertThat(fresh(TENANT, batch.id()).updatedCount()).isEqualTo(2);
        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo("APPLIED");
    }

    @Test
    void aLadderRemovedBetweenTheCheckAndTheConfirmFailsTheBatchAndItCanBeConfirmedAgain() {
        String tech = ladder(TENANT, "Tech");
        DepartmentBatch batch = upload(TENANT, List.of(ladderRow(1, "ENG", "Engineering", null, "Tech")));
        jdbc.update("delete from org_ladder where id = :id", Map.of("id", tech));

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo("FAILED");
        assertThat(fresh(TENANT, batch.id()).message()).contains("Unknown ladder Tech");
        assertThat(store.findUnit(TENANT, "ENG")).isEmpty();
    }

    // ---- upload: validate and stage, change nothing ---------------------------------------------------

    @Test
    void uploadValidatesEveryRowAndChangesNothingLive() {
        unit(TENANT, "ROOT", "Company", null, null);
        int before = units();

        DepartmentBatch batch = upload(TENANT, List.of(row(1, "HR", "Human Resources", "ROOT"), row(2, "BAD", "Orphan", "NOPE"),
                row(3, "IT", "IT", null)));

        assertThat(batch.status()).isEqualTo(OrgStructureConstants.IMPORT_VALIDATED);
        assertThat(batch.totalRows()).isEqualTo(3);
        assertThat(batch.validRows()).isEqualTo(2);
        assertThat(batch.errorRows()).isEqualTo(1);
        assertThat(batch.createdBy()).isEqualTo(ACTOR);
        assertThat(units()).as("nothing is created before the import is confirmed").isEqualTo(before);
        assertThat(count("select count(*) from org_dept_import_row where import_id = :id", "id", batch.id())).isEqualTo(3);
        assertThat(events).isEmpty();
    }

    @Test
    void theRejectedRowsAreListedWithTheirCodesInRowOrderAndCanBePaged() {
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "A", "A", "NOPE1"), row(2, "B", "B", null), row(3, "C", "C", "NOPE2"),
                row(4, "D", "D", "NOPE3")));

        List<DepartmentRow> all = service.errors(TENANT, batch.id(), 0, 50);
        assertThat(all).extracting(r -> r.input().rowNo()).containsExactly(1, 3, 4);
        assertThat(all.get(0).errors()).containsExactly("PARENT_UNKNOWN:NOPE1");
        assertThat(service.errors(TENANT, batch.id(), 1, 1)).extracting(r -> r.input().rowNo()).containsExactly(3);
        assertThat(service.errors(TENANT, batch.id(), 3, 50)).extracting(r -> r.input().rowNo()).containsExactly(4);
    }

    @Test
    void anEmptyFileATooBigFileAndAMissingDocumentAreRefusedWithAPlainMessage() {
        documents.put("empty", List.of());
        assertThatThrownBy(() -> service.upload(TENANT, ACTOR, "empty")).isInstanceOf(OrgStructureException.class)
                .hasMessage("The file has no data rows");

        List<DepartmentRowInput> many = new ArrayList<>();
        for (int i = 1; i <= 1001; i++) {
            many.add(row(i, "C" + i, "Name " + i, null));
        }
        documents.put("many", many);
        assertThatThrownBy(() -> service.upload(TENANT, ACTOR, "many")).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("at most 1000");

        assertThatThrownBy(() -> service.upload(TENANT, ACTOR, "nope")).isInstanceOf(OrgStructureException.class)
                .extracting(e -> ((OrgStructureException) e).kind()).isEqualTo(OrgStructureException.Kind.NOT_FOUND);
    }

    @Test
    void aDocumentStoreOutageSurfacesAsAnOutageAndStagesNothing() {
        fileSourceFailure = new DownstreamUnavailableException("dms-service", DownstreamUnavailableException.Reason.FAILED, null);

        assertThatThrownBy(() -> service.upload(TENANT, ACTOR, "any")).isInstanceOf(DownstreamUnavailableException.class);
        assertThat(count("select count(*) from org_dept_import")).isZero();
    }

    // ---- confirm: parents first, through the org builder's operations --------------------------------

    @Test
    void confirmCreatesTheWholeTreeParentsBeforeChildrenEvenWhenTheFileListsChildrenFirst() {
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "TEAM", "Team", "DEPT"), row(2, "DEPT", "Department", "DIV"),
                row(3, "DIV", "Division", "ROOT"), row(4, "ROOT", "Company", null)));

        DepartmentBatch applied = service.confirm(TENANT, ACTOR, batch.id());

        assertThat(applied.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(fresh(TENANT, batch.id())).satisfies(b -> {
            assertThat(b.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
            assertThat(b.createdCount()).isEqualTo(4);
            assertThat(b.updatedCount()).isZero();
            assertThat(b.appliedAt()).isNotNull();
        });
        assertThat(state(TENANT, "TEAM").parentId()).isEqualTo(id(TENANT, "DEPT"));
        assertThat(state(TENANT, "DEPT").parentId()).isEqualTo(id(TENANT, "DIV"));
        assertThat(state(TENANT, "DIV").parentId()).isEqualTo(id(TENANT, "ROOT"));
        assertThat(state(TENANT, "ROOT").parentId()).isNull();
        // the closure table (everyone below a unit in one join) is right: the same operations as the org builder maintained it
        assertThat(closureBelow(TENANT, "ROOT")).isEqualTo(4);
        assertThat(closureBelow(TENANT, "DIV")).isEqualTo(3);
        assertThat(closureBelow(TENANT, "TEAM")).isEqualTo(1);
        assertThat(count("select count(*) from org_import_row")).as("the employee import's tables are not used").isZero();
        assertThat(events).containsExactly(new OrgStructureChanged(TENANT));
        assertThat(audits).containsExactly(TENANT + "|" + ACTOR + "|true|4|4|4|0|null");
    }

    @Test
    void aDeepChainListedInTheWorstOrderIsStillCreatedAcrossPasses() {
        List<DepartmentRowInput> rows = new ArrayList<>();
        for (int level = 12; level >= 1; level--) {
            rows.add(row(13 - level, "L" + level, "Level " + level, level == 1 ? null : "L" + (level - 1)));
        }
        DepartmentBatch batch = upload(TENANT, rows);
        assertThat(batch.validRows()).isEqualTo(12);

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(fresh(TENANT, batch.id()).createdCount()).isEqualTo(12);
        assertThat(closureBelow(TENANT, "L1")).isEqualTo(12);
    }

    @Test
    void newDepartmentsTakeTheLadderOfTheirParentSoPeopleCanBePlacedInThem() {
        String ladder = ladder(TENANT, "Tech");
        unit(TENANT, "TECH", "Technical", null, ladder);
        unit(TENANT, "PLAIN", "No ladder here", null, null);

        DepartmentBatch batch = upload(TENANT, List.of(row(1, "BACKEND", "Backend", "TECH"), row(2, "SUB", "Under plain", "PLAIN"),
                row(3, "TOP", "Top level", null)));
        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(state(TENANT, "BACKEND").ladderId()).isEqualTo(ladder);
        assertThat(state(TENANT, "SUB").ladderId()).isNull();
        assertThat(state(TENANT, "TOP").ladderId()).isNull();
    }

    @Test
    void theUnitTypeIsStoredOnNewDepartmentsAndLeftAloneOnExistingOnes() {
        unit(TENANT, "OLD", "Old", null, null);
        jdbc.update("update org_unit set unit_type = 'Division' where code = 'OLD'", Map.of());
        DepartmentBatch batch = upload(TENANT, List.of(new DepartmentRowInput(1, "NEW", "New", null, "Department"),
                new DepartmentRowInput(2, "OLD", "Old renamed", null, "Team")));

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(state(TENANT, "NEW").unitType()).isEqualTo("Department");
        assertThat(state(TENANT, "OLD").unitType()).as("the org builder cannot change a unit's type either").isEqualTo("Division");
        assertThat(state(TENANT, "OLD").name()).isEqualTo("Old renamed");
    }

    // ---- update versus create --------------------------------------------------------------------------

    @Test
    void anExistingCodeIsRenamedAndMovedAndOnlyRealChangesAreCountedAsUpdates() {
        unit(TENANT, "A", "Alpha", null, null);
        unit(TENANT, "B", "Beta", null, null);
        unit(TENANT, "C", "Gamma", null, null);
        unit(TENANT, "D", "Delta", "A", null);
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "A", "Alpha renamed", null), row(2, "B", "Beta", "A"),
                row(3, "C", "Gamma", null), row(4, "D", "Delta", "A"), row(5, "E", "Epsilon", "B")));

        service.confirm(TENANT, ACTOR, batch.id());

        DepartmentBatch done = fresh(TENANT, batch.id());
        assertThat(done.createdCount()).isEqualTo(1);
        assertThat(done.updatedCount()).as("A renamed and B moved; C and D were already as the file says").isEqualTo(2);
        assertThat(state(TENANT, "A").name()).isEqualTo("Alpha renamed");
        assertThat(state(TENANT, "B").parentId()).isEqualTo(id(TENANT, "A"));
        assertThat(state(TENANT, "E").parentId()).isEqualTo(id(TENANT, "B"));
        assertThat(closureBelow(TENANT, "A")).as("A, D, B and below B: E").isEqualTo(4);
    }

    @Test
    void aBlankParentOnAnExistingDepartmentLeavesItWhereItIs() {
        unit(TENANT, "ROOT", "Company", null, null);
        unit(TENANT, "MID", "Middle", "ROOT", null);
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "MID", "Middle renamed", null)));

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(state(TENANT, "MID").parentId()).isEqualTo(id(TENANT, "ROOT"));
        assertThat(state(TENANT, "MID").name()).isEqualTo("Middle renamed");
    }

    @Test
    void importingTheSameFileAgainChangesNothing() {
        List<DepartmentRowInput> file = List.of(row(1, "ROOT", "Company", null), row(2, "HR", "Human Resources", "ROOT"));
        service.confirm(TENANT, ACTOR, upload(TENANT, file).id());
        int unitsAfterFirst = units();

        DepartmentBatch second = upload(TENANT, file);
        service.confirm(TENANT, ACTOR, second.id());

        assertThat(units()).isEqualTo(unitsAfterFirst);
        assertThat(fresh(TENANT, second.id()).createdCount()).isZero();
        assertThat(fresh(TENANT, second.id()).updatedCount()).isZero();
        assertThat(fresh(TENANT, second.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(events).as("a no-op import announces nothing").hasSize(1);
    }

    // ---- life cycle: cancel, double confirm, queue full, failure and resume ---------------------------

    @Test
    void aValidatedImportCanBeCancelledAndThenNeverConfirmed() {
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "HR", "HR", null)));

        service.cancel(TENANT, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_CANCELLED);
        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .extracting(e -> ((OrgStructureException) e).kind()).isEqualTo(OrgStructureException.Kind.CONFLICT);
        assertThat(units()).isZero();
    }

    @Test
    void anAppliedImportCannotBeCancelledOrConfirmedAgain() {
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "HR", "HR", null)));
        service.confirm(TENANT, ACTOR, batch.id());

        assertThatThrownBy(() -> service.cancel(TENANT, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("Only a validated import can be cancelled");
        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("cannot be confirmed");
    }

    @Test
    void aSecondConfirmWhileTheFirstIsApplyingIsRefusedAndTheWorkRunsOnlyOnce() {
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "HR", "HR", null)));
        executor.hold = true;

        DepartmentBatch first = service.confirm(TENANT, ACTOR, batch.id());

        assertThat(first.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLYING);
        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("APPLYING");
        assertThat(executor.held).hasSize(1);
        executor.runHeld();
        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(fresh(TENANT, batch.id()).createdCount()).isEqualTo(1);
    }

    @Test
    void aFullQueueEndsTheBatchFailedSoItCanBeConfirmedAgainLater() {
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "HR", "HR", null)));
        executor.reject = true;

        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("Another import is running");
        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        executor.reject = false;

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(units()).isEqualTo(1);
    }

    @Test
    void anImportWithNoValidRowCannotBeConfirmed() {
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "A", "A", "NOPE")));

        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessage("There are no valid rows to import");
    }

    @Test
    void aFailureWhileApplyingEndsTheBatchFailedAndConfirmingAgainResumesWhereItStopped() {
        unit(TENANT, "ROOT", "Company", null, null);
        unit(TENANT, "MID", "Middle", null, null);
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "NEWA", "New A", "ROOT"), row(2, "NEWB", "New B", "ROOT"),
                row(3, "ROOT", "Company", "MID")));
        // meanwhile somebody puts MID under ROOT, so moving ROOT under MID would now make a loop
        structure.apply(TENANT, new Change(null, null, 0, ChangeType.UNIT_MOVE, Map.of("id", id(TENANT, "MID"), "newParentId", id(TENANT, "ROOT"))), TODAY);

        service.confirm(TENANT, ACTOR, batch.id());

        DepartmentBatch failed = fresh(TENANT, batch.id());
        assertThat(failed.status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        assertThat(failed.message()).startsWith("Row 3:").contains("under itself");
        assertThat(failed.createdCount()).as("the first chunk was committed before the failing one").isEqualTo(2);
        assertThat(audits.get(0)).contains("|false|").contains("Row 3");
        // fix the cause and confirm again: only the unfinished row is done
        structure.apply(TENANT, new Change(null, null, 0, ChangeType.UNIT_MOVE, Map.of("id", id(TENANT, "MID"))), TODAY);

        service.confirm(TENANT, ACTOR, batch.id());

        DepartmentBatch resumed = fresh(TENANT, batch.id());
        assertThat(resumed.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(resumed.createdCount()).isEqualTo(2);
        assertThat(resumed.updatedCount()).isEqualTo(1);
        assertThat(state(TENANT, "ROOT").parentId()).isEqualTo(id(TENANT, "MID"));
    }

    @Test
    void aParentThatDisappearsBeforeConfirmEndsTheBatchFailedWithAClearReason() {
        unit(TENANT, "ROOT", "Company", null, null);
        DepartmentBatch batch = upload(TENANT, List.of(row(1, "HR", "HR", "ROOT")));
        structure.apply(TENANT, new Change(null, null, 0, ChangeType.UNIT_ARCHIVE, Map.of("id", id(TENANT, "ROOT"))), TODAY);

        service.confirm(TENANT, ACTOR, batch.id());

        DepartmentBatch failed = fresh(TENANT, batch.id());
        assertThat(failed.status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        assertThat(failed.message()).contains("parent department could not be found");
        assertThat(store.findUnit(TENANT, "HR")).isEmpty();
    }

    // ---- organizations ---------------------------------------------------------------------------------

    @Test
    void oneOrganizationCannotSeeConfirmCancelOrListAnotherOnesImport() {
        DepartmentBatch mine = upload(TENANT, List.of(row(1, "A", "A", "NOPE"), row(2, "B", "B", null)));

        assertThatThrownBy(() -> service.batch(OTHER, mine.id())).isInstanceOf(OrgStructureException.class)
                .extracting(e -> ((OrgStructureException) e).kind()).isEqualTo(OrgStructureException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> service.errors(OTHER, mine.id(), 0, 50)).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> service.confirm(OTHER, ACTOR, mine.id())).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> service.cancel(OTHER, mine.id())).isInstanceOf(OrgStructureException.class);
        assertThat(service.recent(OTHER, 50)).isEmpty();
        assertThat(store.errors(OTHER, mine.id(), 0, 50)).as("the store itself filters on the organization").isEmpty();
        assertThat(store.claimForApply(OTHER, mine.id())).isFalse();
        assertThat(store.cancel(OTHER, mine.id())).isFalse();
        assertThat(service.recent(TENANT, 50)).extracting(DepartmentBatch::id).containsExactly(mine.id());
        assertThat(fresh(TENANT, mine.id()).status()).as("untouched by the other organization").isEqualTo(OrgStructureConstants.IMPORT_VALIDATED);
    }

    @Test
    void aFileIsCheckedAgainstTheCallersOrganizationOnlyAndAppliesOnlyThere() {
        unit(TENANT, "ROOT", "Company of one", null, null);
        unit(OTHER, "ROOT", "Company of two", null, null);
        unit(TENANT, "ONLY1", "Only in one", null, null);

        DepartmentBatch batch = upload(OTHER, List.of(row(1, "HR", "HR", "ROOT"), row(2, "X", "X", "ONLY1")));

        assertThat(batch.validRows()).isEqualTo(1);
        assertThat(service.errors(OTHER, batch.id(), 0, 50).get(0).errors()).containsExactly("PARENT_UNKNOWN:ONLY1");
        service.confirm(OTHER, ACTOR, batch.id());
        assertThat(store.findUnit(OTHER, "HR")).isPresent();
        assertThat(store.findUnit(TENANT, "HR")).as("the other organization is untouched").isEmpty();
        assertThat(state(OTHER, "HR").parentId()).isEqualTo(id(OTHER, "ROOT")).isNotEqualTo(id(TENANT, "ROOT"));
        assertThat(state(TENANT, "ROOT").name()).isEqualTo("Company of one");
    }

    @Test
    void theSameCodeInTwoOrganizationsIsTwoDepartments() {
        service.confirm(TENANT, ACTOR, upload(TENANT, List.of(row(1, "HR", "One HR", null))).id());
        service.confirm(OTHER, ACTOR, upload(OTHER, List.of(row(1, "HR", "Two HR", null))).id());

        assertThat(state(TENANT, "HR").name()).isEqualTo("One HR");
        assertThat(state(OTHER, "HR").name()).isEqualTo("Two HR");
        assertThat(units()).isEqualTo(2);
    }

    // ---- rollback guard -----------------------------------------------------------------------------------

    @Test
    void rollingBackAPublishIsRefusedOnceADepartmentImportWasAppliedAfterIt() {
        ChangeSet draft = structureService.openDraft(TENANT, ACTOR, null);
        structureService.addChange(TENANT, draft.id(), ChangeType.UNIT_CREATE, Map.of("code", "ROOT", "name", "Company"));
        assertThat(structureService.publish(TENANT, draft.id(), ACTOR).applied()).isTrue();
        clock.advanceMinutes(5);
        service.confirm(TENANT, ACTOR, upload(TENANT, List.of(row(1, "HR", "HR", "ROOT"))).id());

        assertThatThrownBy(() -> structureService.rollbackLatest(TENANT, ACTOR)).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("A department import was applied after this publish");
        assertThat(store.findUnit(TENANT, "HR")).isPresent();
    }

    @Test
    void rollingBackIsStillAllowedWhenNoDepartmentImportCameAfterThePublish() {
        ChangeSet draft = structureService.openDraft(TENANT, ACTOR, null);
        structureService.addChange(TENANT, draft.id(), ChangeType.UNIT_CREATE, Map.of("code", "ROOT", "name", "Company"));
        structureService.publish(TENANT, draft.id(), ACTOR);

        structureService.rollbackLatest(TENANT, ACTOR);

        assertThat(store.findUnit(TENANT, "ROOT")).isEmpty();
    }
}
