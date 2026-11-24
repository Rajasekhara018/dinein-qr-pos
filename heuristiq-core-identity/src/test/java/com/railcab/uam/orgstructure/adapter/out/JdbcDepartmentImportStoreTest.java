package com.heuristiq.uam.orgstructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentReference;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentRowInput;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.ExistingUnit;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.StagedDepartmentRow;
import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentBatch;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;

/** The department import store on H2 with the project's own migration scripts: paging caps, what is read from the tree, the guard query. */
class JdbcDepartmentImportStoreTest {
    private static final String TENANT = "t-1";
    private static final String OTHER = "t-2";

    private NamedParameterJdbcTemplate jdbc;
    private JdbcDepartmentImportStore store;
    private final MutableClock clock = new MutableClock();

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-26T10:00:00Z");

        void advanceMinutes(long minutes) { now = now.plusSeconds(minutes * 60); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:deptstore_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V19__org_department_import.sql"),
                new ClassPathResource("db/migration/V20__org_department_import_ladder.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new JdbcDepartmentImportStore(jdbc, clock);
    }

    private static DepartmentRow row(int no, String code, boolean valid) {
        return new DepartmentRow(new DepartmentRowInput(no, code, "Name " + no, null, null),
                valid ? OrgStructureConstants.IMPORT_ROW_VALID : OrgStructureConstants.IMPORT_ROW_INVALID, valid ? List.of() : List.of("X" + no));
    }

    private DepartmentBatch stage(String tenant, int rows) {
        List<DepartmentRow> list = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            list.add(row(i, "C" + i, i % 2 == 1));
        }
        return store.stage(tenant, "actor", "doc-" + UUID.randomUUID(), "f.csv", list, 0);
    }

    private void unit(String id, String tenant, String parentId, String code, String status) {
        jdbc.update("insert into org_unit (id, tenant_id, parent_id, code, name, unit_type, status, version, created_at, updated_at) values "
                + "(:id, :t, :p, :c, :n, 'Team', :s, 0, current_timestamp, current_timestamp)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource().addValue("id", id).addValue("t", tenant)
                        .addValue("p", parentId).addValue("c", code).addValue("n", "Name " + code).addValue("s", status));
    }

    @Test
    void stagingRecordsTheCountsTheRowsAndWhoStartedIt() {
        DepartmentBatch batch = stage(TENANT, 5);

        assertThat(batch.status()).isEqualTo("VALIDATED");
        assertThat(batch.totalRows()).isEqualTo(5);
        assertThat(batch.validRows()).isEqualTo(3);
        assertThat(batch.errorRows()).isEqualTo(2);
        assertThat(batch.createdBy()).isEqualTo("actor");
        assertThat(batch.fileName()).isEqualTo("f.csv");
        assertThat(batch.createdAt()).isEqualTo(clock.instant());
        assertThat(batch.appliedAt()).isNull();
    }

    @Test
    void theLadderOfARowAndTheCountOfDepartmentsWithoutOneAreStoredAndReadBack() {
        List<DepartmentRow> rows = List.of(new DepartmentRow(new DepartmentRowInput(1, "A", "A", null, null, "Tech"), "VALID", List.of()),
                new DepartmentRow(new DepartmentRowInput(2, "B", "B", null, null, "Nope"), "INVALID", List.of("LADDER_UNKNOWN:Nope")));

        DepartmentBatch batch = store.stage(TENANT, "actor", "doc", "f.csv", rows, 4);

        assertThat(batch.withoutLadder()).isEqualTo(4);
        assertThat(store.errors(TENANT, batch.id(), 0, 10)).extracting(r -> r.input().ladder()).containsExactly("Nope");
        assertThat(store.rows(batch.id(), "VALID", 0, 10)).extracting(r -> r.input().ladder()).containsExactly("Tech");
    }

    @Test
    void laddersAreFoundByNameIgnoringCaseAndBlanksWithinTheOrganizationOnly() {
        jdbc.update("insert into org_ladder (id, tenant_id, name, created_at) values ('l-1', :t, 'Tech Ladder', current_timestamp)",
                Map.of("t", TENANT));
        jdbc.update("insert into org_ladder (id, tenant_id, name, created_at) values ('l-2', :t, 'Tech Ladder', current_timestamp)",
                Map.of("t", OTHER));

        assertThat(store.findLadderId(TENANT, "  tech LADDER ")).contains("l-1");
        assertThat(store.findLadderId(OTHER, "Tech Ladder")).contains("l-2");
        assertThat(store.findLadderId(TENANT, "Other")).isEmpty();
        assertThat(store.loadReference(TENANT).ladderId("TECH ladder")).isEqualTo("l-1");
        assertThat(store.loadReference("t-3").ladderId("Tech Ladder")).isNull();
    }

    @Test
    void aFileLargerThanOneInsertChunkIsStagedWhole() {
        int rows = OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK + 250;

        DepartmentBatch batch = stage(TENANT, rows);

        assertThat(jdbc.queryForObject("select count(*) from org_dept_import_row where import_id = :id", Map.of("id", batch.id()), Integer.class))
                .isEqualTo(rows);
    }

    @Test
    void theRejectedRowsPageByRowNumberAndTheLimitIsCapped() {
        DepartmentBatch batch = stage(TENANT, 3 * OrgStructureConstants.MAX_PAGE_SIZE + 10);   // half of the rows are rejected

        List<DepartmentRow> first = store.errors(TENANT, batch.id(), 0, 10_000);

        assertThat(first).hasSize(OrgStructureConstants.MAX_PAGE_SIZE);
        assertThat(first).allMatch(r -> !r.valid());
        assertThat(first.get(0).input().rowNo()).isEqualTo(2);
        int last = first.get(first.size() - 1).input().rowNo();
        assertThat(store.errors(TENANT, batch.id(), last, 3).get(0).input().rowNo()).isGreaterThan(last);
        assertThat(store.errors(TENANT, batch.id(), 0, 0)).as("a limit below one is lifted to one").hasSize(1);
        assertThat(store.errors(TENANT, batch.id(), 0, 5).get(0).errors()).containsExactly("X2");
    }

    @Test
    void recentListsTheNewestFirstAndCapsTheLimit() {
        DepartmentBatch older = stage(TENANT, 1);
        clock.advanceMinutes(10);
        DepartmentBatch newer = stage(TENANT, 1);
        for (int i = 0; i < OrgStructureConstants.MAX_PAGE_SIZE + 5; i++) {
            stage(OTHER, 1);
        }

        assertThat(store.recent(TENANT, 50)).extracting(DepartmentBatch::id).containsExactly(newer.id(), older.id());
        assertThat(store.recent(OTHER, 100_000)).hasSize(OrgStructureConstants.MAX_PAGE_SIZE);
        assertThat(store.recent(TENANT, 0)).hasSize(1);
    }

    @Test
    void theLifeCycleStatesMoveOnlyAlongAllowedSteps() {
        DepartmentBatch batch = stage(TENANT, 2);

        assertThat(store.cancel(OTHER, batch.id())).isFalse();
        assertThat(store.claimForApply(OTHER, batch.id())).isFalse();
        assertThat(store.claimForApply(TENANT, batch.id())).isTrue();
        assertThat(store.claimForApply(TENANT, batch.id())).as("only one request can take it").isFalse();
        assertThat(store.cancel(TENANT, batch.id())).as("an applying import cannot be cancelled").isFalse();
        store.finish(batch.id(), OrgStructureConstants.IMPORT_FAILED, "boom");
        assertThat(store.find(TENANT, batch.id()).orElseThrow()).satisfies(b -> {
            assertThat(b.status()).isEqualTo("FAILED");
            assertThat(b.message()).isEqualTo("boom");
            assertThat(b.appliedAt()).isNotNull();
        });
        assertThat(store.claimForApply(TENANT, batch.id())).as("a failed import can be confirmed again").isTrue();
    }

    @Test
    void stagedRowsAreReadInRowOrderAfterTheGivenRowAndMarkedAsApplied() {
        DepartmentBatch batch = stage(TENANT, 6);

        List<StagedDepartmentRow> valid = store.rows(batch.id(), "VALID", 0, 2);
        assertThat(valid).extracting(r -> r.input().rowNo()).containsExactly(1, 3);
        assertThat(store.rows(batch.id(), "VALID", 3, 10)).extracting(r -> r.input().rowNo()).containsExactly(5);

        store.markRows(batch.id(), List.of(1, 3), "APPLIED");
        store.addCounts(batch.id(), 2, 1);
        store.addCounts(batch.id(), 1, 0);

        assertThat(store.rows(batch.id(), "VALID", 0, 10)).extracting(r -> r.input().rowNo()).containsExactly(5);
        assertThat(store.rows(batch.id(), "APPLIED", 0, 10)).extracting(r -> r.input().rowNo()).containsExactly(1, 3);
        assertThat(store.find(TENANT, batch.id()).orElseThrow()).satisfies(b -> {
            assertThat(b.createdCount()).isEqualTo(3);
            assertThat(b.updatedCount()).isEqualTo(1);
        });
    }

    @Test
    void theReferenceHoldsEveryDepartmentOfTheOrganizationWithItsParentCodeAndActivity() {
        unit("u-root", TENANT, null, "ROOT", "ACTIVE");
        unit("u-kid", TENANT, "u-root", "KID", "ACTIVE");
        unit("u-old", TENANT, null, "OLD", "ARCHIVED");
        unit("u-other", OTHER, null, "ELSEWHERE", "ACTIVE");

        DepartmentReference reference = store.loadReference(TENANT);

        assertThat(reference.unitsByCode()).containsOnlyKeys("ROOT", "KID", "OLD");
        ExistingUnit kid = reference.unitsByCode().get("KID");
        assertThat(kid.parentCode()).isEqualTo("ROOT");
        assertThat(kid.active()).isTrue();
        assertThat(kid.unitType()).isEqualTo("Team");
        assertThat(reference.unitsByCode().get("ROOT").parentCode()).isNull();
        assertThat(reference.unitsByCode().get("OLD").active()).isFalse();
    }

    @Test
    void aDepartmentIsFoundByExactCodeWithinTheOrganizationOnly() {
        unit("u-1", TENANT, null, "HR", "ACTIVE");
        unit("u-2", OTHER, null, "HR", "ARCHIVED");

        assertThat(store.findUnit(TENANT, "HR").orElseThrow().active()).isTrue();
        assertThat(store.findUnit(OTHER, "HR").orElseThrow().active()).isFalse();
        assertThat(store.findUnit(TENANT, "hr")).as("codes are case sensitive, like the unique key").isEmpty();
        assertThat(store.findUnit("t-3", "HR")).isEmpty();
    }

    @Test
    void theRollbackGuardSeesOnlyAppliedImportsOfTheSameOrganizationAfterTheGivenTime() {
        Instant before = clock.instant();
        DepartmentBatch applied = stage(TENANT, 2);
        DepartmentBatch failed = stage(TENANT, 2);
        DepartmentBatch otherOrg = stage(OTHER, 2);
        clock.advanceMinutes(5);
        store.finish(applied.id(), OrgStructureConstants.IMPORT_APPLIED, null);
        store.finish(failed.id(), OrgStructureConstants.IMPORT_FAILED, "x");
        store.finish(otherOrg.id(), OrgStructureConstants.IMPORT_APPLIED, null);

        assertThat(store.anyAppliedSince(TENANT, before)).isTrue();
        assertThat(store.anyAppliedSince(TENANT, clock.instant())).as("not after a time that is not before it").isFalse();
        assertThat(store.anyAppliedSince("t-3", before)).isFalse();
    }
}
