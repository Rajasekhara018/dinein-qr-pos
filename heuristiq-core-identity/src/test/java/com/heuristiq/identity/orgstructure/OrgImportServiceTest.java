package com.heuristiq.identity.orgstructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
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
import com.heuristiq.identity.orgstructure.application.OrgImportService;
import com.heuristiq.identity.orgstructure.application.OrgStructureService;
import com.heuristiq.identity.orgstructure.domain.ChangeSetStore;
import com.heuristiq.identity.orgstructure.domain.ChangeType;
import com.heuristiq.identity.orgstructure.domain.EmployeeStore;
import com.heuristiq.identity.orgstructure.domain.ImportFileSource;
import com.heuristiq.identity.orgstructure.domain.ImportLimits;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportBatch;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRowInput;
import com.heuristiq.identity.orgstructure.domain.ImportStore;
import com.heuristiq.identity.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;

/**
 * The bulk import end to end on H2: a file is validated and staged without touching the organization, only a
 * confirmed batch is applied, and applying it twice changes nothing the second time.
 */
class OrgImportServiceTest {
    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "admin";

    private OrgStructureService structureService;
    private OrgImportService importService;
    /** Stands in for the document store: document id to the rows its file holds. */
    private final Map<String, List<ImportRowInput>> documents = new HashMap<>();
    private NamedParameterJdbcTemplate jdbc;

    /** Runs submitted work on the calling thread so the test needs no waiting. */
    private static final class InlineExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:orgimport_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V8__org_import.sql"),
                new ClassPathResource("db/migration/V21__org_ladder_import_and_heads.sql"),
                new ClassPathResource("db/migration/V10__org_publish_snapshot.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
        var tx = new DataSourceTransactionManager(dataSource);

        OrgStructureStore structure = (OrgStructureStore) construct("JdbcOrgStructureStore", jdbc, clock);
        ChangeSetStore changes = (ChangeSetStore) construct("JdbcChangeSetStore", jdbc, new ObjectMapper(), clock);
        EmployeeStore employees = (EmployeeStore) construct("JdbcEmployeeStore", jdbc, clock);
        ImportStore imports = (ImportStore) construct("JdbcImportStore", jdbc, clock);
        com.heuristiq.identity.orgstructure.domain.StructureSnapshotStore snapshots =
                (com.heuristiq.identity.orgstructure.domain.StructureSnapshotStore) construct("JdbcStructureSnapshotStore", jdbc, clock);
        structureService = new OrgStructureService(structure, changes, employees, snapshots, imports, tx, clock);
        ImportFileSource files = (documentId, callerId) -> {
            List<ImportRowInput> rows = documents.get(documentId);
            if (rows == null) {
                throw OrgStructureException.notFound("The uploaded file was not found: " + documentId);
            }
            return new ImportFileSource.ImportFile(documentId, documentId + ".csv", rows);
        };
        importService = new OrgImportService(imports, files, employees, structure, new ImportLimits(1000, 2),
                new InlineExecutor(), tx, clock);
        createOrganization();
    }

    /** A Technical department with a TL / SE ladder, plus an empty HR department that has no ladder. */
    private void createOrganization() {
        ChangeSet draft = structureService.openDraft(TENANT, ACTOR, null);
        String ladder = add(draft, ChangeType.LADDER_CREATE, Map.of("name", "Tech"));
        add(draft, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "TL", "rank", 3));
        add(draft, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        add(draft, ChangeType.UNIT_CREATE, Map.of("code", "TECH", "name", "Technical", "ladderId", ladder));
        add(draft, ChangeType.UNIT_CREATE, Map.of("code", "HR", "name", "Human Resources"));
        assertThat(structureService.publish(TENANT, draft.id(), ACTOR).applied()).isTrue();
    }

    private String add(ChangeSet draft, ChangeType type, Map<String, Object> payload) {
        return String.valueOf(structureService.addChange(TENANT, draft.id(), type, payload).payload().get("id"));
    }

    /** Puts the rows in the fake document store and starts an import from them, as the web app does after uploading. */
    private ImportBatch upload(String fileName, List<ImportRowInput> rows) {
        String documentId = "doc-" + fileName + "-" + documents.size();
        documents.put(documentId, rows);
        return importService.upload(TENANT, ACTOR, documentId);
    }

    private static ImportRowInput row(int no, String code, String name, String dept, String position, String manager) {
        return new ImportRowInput(no, code, name, code.toLowerCase() + "@x.com", null, dept, position, manager, "2024-01-15");
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("select count(*) from " + table, Map.of(), Integer.class);
        return n == null ? 0 : n;
    }

    private static ImportRowInput headRow(int no, String code, String position, String isHead) {
        return new ImportRowInput(no, code, "Name " + code, null, null, "TECH", position, null, null, isHead);
    }

    private String headOfTech() {
        return jdbc.query("select e.employee_code as c from org_unit u join org_employee e on e.id = u.head_employee_id where u.code = 'TECH'",
                Map.of(), (rs, i) -> rs.getString("c")).stream().findFirst().orElse(null);
    }

    @Test
    void anIsHeadYesRowMakesThePersonTheirDepartmentsHeadAndTheBatchCountsIt() {
        ImportBatch batch = upload("heads.csv", List.of(headRow(1, "E1", "TL", "yes"), headRow(2, "E2", "SE", null),
                headRow(3, "E3", "SE", "no")));

        importService.confirm(TENANT, batch.id());

        assertThat(importService.batch(TENANT, batch.id()).headsSet()).isEqualTo(1);
        assertThat(importService.batch(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(headOfTech()).isEqualTo("E1");
    }

    @Test
    void runningTheSameHeadAgainSetsNothingNew() {
        importService.confirm(TENANT, upload("a.csv", List.of(headRow(1, "E1", "TL", "yes"))).id());

        ImportBatch second = upload("b.csv", List.of(headRow(1, "E1", "TL", "yes")));
        importService.confirm(TENANT, second.id());

        assertThat(importService.batch(TENANT, second.id()).headsSet()).isZero();
        assertThat(headOfTech()).isEqualTo("E1");
    }

    @Test
    void aDifferentExistingHeadIsReplacedThroughClearThenSet() {
        importService.confirm(TENANT, upload("a.csv", List.of(headRow(1, "E1", "TL", "yes"), headRow(2, "E2", "SE", "no"))).id());

        ImportBatch second = upload("b.csv", List.of(headRow(1, "E2", "SE", "yes")));
        importService.confirm(TENANT, second.id());

        assertThat(importService.batch(TENANT, second.id()).headsSet()).isEqualTo(1);
        assertThat(headOfTech()).isEqualTo("E2");
    }

    @Test
    void rowsWithoutIsHeadOrWithNoLeaveTheCurrentHeadAlone() {
        importService.confirm(TENANT, upload("a.csv", List.of(headRow(1, "E1", "TL", "yes"))).id());

        ImportBatch second = upload("b.csv", List.of(headRow(1, "E2", "SE", "no"), headRow(2, "E3", "SE", null),
                headRow(3, "E1", "TL", "no")));
        importService.confirm(TENANT, second.id());

        assertThat(importService.batch(TENANT, second.id()).headsSet()).isZero();
        assertThat(headOfTech()).isEqualTo("E1");
    }

    @Test
    void aSecondHeadForOneDepartmentInTheFileIsRejectedAndNeverApplied() {
        ImportBatch batch = upload("c.csv", List.of(headRow(1, "E1", "TL", "yes"), headRow(2, "E2", "SE", "yes"),
                headRow(3, "E3", "SE", "maybe")));

        assertThat(importService.errors(TENANT, batch.id(), 0, 50)).extracting(r -> r.errors().get(0))
                .containsExactly("HEAD_DUPLICATE:TECH:1", "IS_HEAD_INVALID");
        importService.confirm(TENANT, batch.id());
        assertThat(headOfTech()).isEqualTo("E1");
        assertThat(importService.batch(TENANT, batch.id()).headsSet()).isEqualTo(1);
    }

    @Test
    void uploadValidatesEveryRowAndChangesNothingLive() {
        ImportBatch batch = upload("staff.csv", List.of(
                row(1, "E1", "Asha", "TECH", "TL", null),
                row(2, "E2", "Ravi", "TECH", "SE", "E1"),
                row(3, "E3", "Meera", "NOPE", "SE", null),
                row(4, "E4", "Kiran", "TECH", "Accountant", null)));

        assertThat(batch.status()).isEqualTo(OrgStructureConstants.IMPORT_VALIDATED);
        assertThat(batch.totalRows()).isEqualTo(4);
        assertThat(batch.validRows()).isEqualTo(2);
        assertThat(batch.errorRows()).isEqualTo(2);
        assertThat(count("org_employee")).isZero();
        assertThat(count("org_assignment")).isZero();

        var errors = importService.errors(TENANT, batch.id(), 0, 50);
        assertThat(errors).extracting(r -> r.input().rowNo()).containsExactly(3, 4);
        assertThat(errors.get(0).errors()).containsExactly("DEPARTMENT_UNKNOWN:NOPE");
        assertThat(errors.get(1).errors()).containsExactly("POSITION_NOT_IN_LADDER:Accountant");
    }

    @Test
    void confirmCreatesEmployeesPlacesThemAndSetsReportingLinesSkippingRejectedRows() {
        ImportBatch batch = upload("staff.csv", List.of(
                row(1, "E1", "Asha", "TECH", "TL", null),
                row(2, "E2", "Ravi", "TECH", "SE", "E1"),
                row(3, "E3", "Meera", "NOPE", "SE", null),
                row(4, "E5", "Dev", "TECH", "SE", "E2"),
                row(5, "E6", "Ira", "TECH", "SE", "E1")));

        importService.confirm(TENANT, batch.id());   // the test executor runs the whole import inline

        ImportBatch done = importService.batch(TENANT, batch.id());
        assertThat(done.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(done.createdCount()).isEqualTo(4);
        assertThat(done.updatedCount()).isZero();
        assertThat(count("org_employee")).isEqualTo(4);

        String tech = jdbc.queryForObject("select id from org_unit where code = 'TECH'", Map.of(), String.class);
        var people = structureService.people(TENANT, tech, null, 50);
        assertThat(people).extracting("employeeCode").containsExactlyInAnyOrder("E1", "E2", "E5", "E6");
        assertThat(people).filteredOn(p -> p.employeeCode().equals("E1")).extracting("positionName").containsExactly("TL");
        assertThat(people).filteredOn(p -> p.employeeCode().equals("E5")).singleElement()
                .satisfies(p -> assertThat(p.managerEmployeeId()).isNotNull());
        assertThat(count("org_reporting_line")).isEqualTo(3);
        Integer unfinished = jdbc.queryForObject(
                "select count(*) from org_import_row where import_id = :id and status in ('VALID','PLACED')",
                Map.of("id", batch.id()), Integer.class);
        assertThat(unfinished).isZero();
    }

    @Test
    void runningTheSameFileAgainUpdatesInsteadOfDuplicatingAndDoesNotChurnAssignments() {
        List<ImportRowInput> file = List.of(
                row(1, "E1", "Asha", "TECH", "TL", null),
                row(2, "E2", "Ravi", "TECH", "SE", "E1"));
        importService.confirm(TENANT, upload("a.csv", file).id());
        int assignments = count("org_assignment");
        int lines = count("org_reporting_line");

        ImportBatch second = upload("a.csv", List.of(
                row(1, "E1", "Asha Rao", "TECH", "TL", null),
                row(2, "E2", "Ravi", "TECH", "SE", "E1")));
        importService.confirm(TENANT, second.id());

        ImportBatch done = importService.batch(TENANT, second.id());
        assertThat(done.createdCount()).isZero();
        assertThat(done.updatedCount()).isEqualTo(2);
        assertThat(count("org_employee")).isEqualTo(2);
        assertThat(count("org_assignment")).as("same placement is not re-assigned").isEqualTo(assignments);
        assertThat(count("org_reporting_line")).isEqualTo(lines);
        assertThat(jdbc.queryForObject("select full_name from org_employee where employee_code = 'E1'", Map.of(), String.class))
                .isEqualTo("Asha Rao");
    }

    @Test
    void movingAnExistingEmployeeToANewPositionEndsTheOldPlacement() {
        importService.confirm(TENANT, upload("a.csv",
                List.of(row(1, "E1", "Asha", "TECH", "SE", null))).id());
        importService.confirm(TENANT, upload("b.csv",
                List.of(row(1, "E1", "Asha", "TECH", "TL", null))).id());

        Integer open = jdbc.queryForObject(
                "select count(*) from org_assignment where end_date is null", Map.of(), Integer.class);
        assertThat(open).isEqualTo(1);
        assertThat(count("org_assignment")).isEqualTo(2);
    }

    @Test
    void aBatchCanOnlyBeConfirmedOnceAndACancelledBatchNeverApplies() {
        ImportBatch batch = upload("a.csv", List.of(row(1, "E1", "Asha", "TECH", "TL", null)));
        importService.confirm(TENANT, batch.id());
        assertThatThrownBy(() -> importService.confirm(TENANT, batch.id())).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> importService.cancel(TENANT, batch.id())).isInstanceOf(OrgStructureException.class);

        ImportBatch other = upload("b.csv", List.of(row(1, "E9", "Zed", "TECH", "SE", null)));
        importService.cancel(TENANT, other.id());
        assertThatThrownBy(() -> importService.confirm(TENANT, other.id())).isInstanceOf(OrgStructureException.class);
        assertThat(jdbc.queryForObject("select count(*) from org_employee where employee_code = 'E9'", Map.of(), Integer.class))
                .isZero();
    }

    @Test
    void anImportWithNoValidRowsCannotBeConfirmed() {
        ImportBatch batch = upload("bad.csv", List.of(row(1, "E1", "Asha", "NOPE", "TL", null)));
        assertThat(batch.validRows()).isZero();
        assertThatThrownBy(() -> importService.confirm(TENANT, batch.id())).isInstanceOf(OrgStructureException.class);
    }

    @Test
    void oversizedAndEmptyFilesAreRefusedAndOtherTenantsCannotSeeABatch() {
        assertThatThrownBy(() -> upload("x.csv", List.of()))
                .isInstanceOf(OrgStructureException.class);
        List<ImportRowInput> tooMany = java.util.stream.IntStream.rangeClosed(1, 1001)
                .mapToObj(i -> row(i, "E" + i, "N" + i, "TECH", "SE", null)).toList();
        assertThatThrownBy(() -> upload("x.csv", tooMany))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("1000");

        ImportBatch batch = upload("a.csv", List.of(row(1, "E1", "Asha", "TECH", "TL", null)));
        assertThatThrownBy(() -> importService.batch("other-tenant", batch.id())).isInstanceOf(OrgStructureException.class);
        assertThat(importService.recent("other-tenant", 10)).isEmpty();
        assertThat(importService.recent(TENANT, 10)).hasSize(1);
    }

    @Test
    void anUnknownDocumentIsReportedAsNotFoundAndStagesNothing() {
        assertThatThrownBy(() -> importService.upload(TENANT, ACTOR, "no-such-document"))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("not found");
        assertThat(count("org_import")).isZero();
    }

    @Test
    void theStagedBatchRemembersWhichDocumentItCameFrom() {
        documents.put("doc-77", List.of(row(1, "E1", "Asha", "TECH", "TL", null)));
        ImportBatch batch = importService.upload(TENANT, ACTOR, "doc-77");
        assertThat(batch.documentId()).isEqualTo("doc-77");
        assertThat(batch.fileName()).isEqualTo("doc-77.csv");
    }

    private static Object construct(String simpleName, Object... args) throws Exception {
        var constructor = Class.forName("com.heuristiq.identity.orgstructure.adapter.out." + simpleName).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
}
