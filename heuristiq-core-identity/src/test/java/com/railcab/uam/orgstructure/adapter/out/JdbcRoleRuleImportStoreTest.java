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

import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleBatch;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleReference;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;

/** The roles import store on H2 with the project's own migration scripts: paging caps, lookups, tenant filters. */
class JdbcRoleRuleImportStoreTest {
    private static final String TENANT = "t-1";
    private static final String OTHER = "t-2";

    private NamedParameterJdbcTemplate jdbc;
    private JdbcRoleRuleImportStore store;
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
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:rulestore_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V22__org_role_rule_import.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new JdbcRoleRuleImportStore(jdbc, clock);
    }

    private static RoleRuleRow row(int no, boolean valid) {
        return new RoleRuleRow(new RoleRuleRowInput(no, "TECH", "Tech", "Engineer " + no, "EMPLOYEE"),
                valid ? OrgStructureConstants.IMPORT_ROW_VALID : OrgStructureConstants.IMPORT_ROW_INVALID, valid ? List.of() : List.of("X" + no));
    }

    private RoleRuleBatch stage(String tenant, int rows) {
        List<RoleRuleRow> list = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            list.add(row(i, i % 2 == 1));
        }
        return store.stage(tenant, "actor", "doc-" + UUID.randomUUID(), "f.csv", list);
    }

    private void unit(String id, String tenant, String code, String status) {
        jdbc.update("insert into org_unit (id, tenant_id, code, name, status, version, created_at, updated_at) values "
                + "(:id, :t, :c, :c, :s, 0, current_timestamp, current_timestamp)", Map.of("id", id, "t", tenant, "c", code, "s", status));
    }

    private void ladder(String id, String tenant, String name) {
        jdbc.update("insert into org_ladder (id, tenant_id, name, created_at) values (:id, :t, :n, current_timestamp)",
                Map.of("id", id, "t", tenant, "n", name));
    }

    private void position(String id, String tenant, String ladderId, String name, int rank) {
        jdbc.update("insert into org_position (id, tenant_id, ladder_id, name, rank_no, managerial) values (:id, :t, :l, :n, :r, false)",
                Map.of("id", id, "t", tenant, "l", ladderId, "n", name, "r", rank));
    }

    @Test
    void stagingRecordsTheCountsTheRowsAsTypedAndWhoStartedIt() {
        RoleRuleBatch batch = stage(TENANT, 5);

        assertThat(batch.status()).isEqualTo("VALIDATED");
        assertThat(batch.totalRows()).isEqualTo(5);
        assertThat(batch.validRows()).isEqualTo(3);
        assertThat(batch.errorRows()).isEqualTo(2);
        assertThat(batch.createdCount()).isZero();
        assertThat(batch.unchangedCount()).isZero();
        assertThat(batch.createdBy()).isEqualTo("actor");
        assertThat(batch.createdAt()).isEqualTo(clock.instant());
        assertThat(store.rows(batch.id(), "VALID", 0, 1).get(0).input()).isEqualTo(new RoleRuleRowInput(1, "TECH", "Tech", "Engineer 1", "EMPLOYEE"));
    }

    @Test
    void aFileLargerThanOneInsertChunkIsStagedWhole() {
        int rows = OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK + 250;

        RoleRuleBatch batch = stage(TENANT, rows);

        assertThat(jdbc.queryForObject("select count(*) from org_role_rule_import_row where import_id = :id", Map.of("id", batch.id()), Integer.class))
                .isEqualTo(rows);
    }

    @Test
    void theRejectedRowsPageByRowNumberAndTheLimitIsCappedAndScopedToTheOrganization() {
        RoleRuleBatch batch = stage(TENANT, 3 * OrgStructureConstants.MAX_PAGE_SIZE + 10);

        List<RoleRuleRow> first = store.errors(TENANT, batch.id(), 0, 10_000);

        assertThat(first).hasSize(OrgStructureConstants.MAX_PAGE_SIZE);
        assertThat(first).allMatch(r -> !r.valid());
        int last = first.get(first.size() - 1).input().rowNo();
        assertThat(store.errors(TENANT, batch.id(), last, 3).get(0).input().rowNo()).isGreaterThan(last);
        assertThat(store.errors(TENANT, batch.id(), 0, 0)).hasSize(1);
        assertThat(store.errors(TENANT, batch.id(), 0, 5).get(0).errors()).containsExactly("X2");
        assertThat(store.errors(OTHER, batch.id(), 0, 5)).isEmpty();
    }

    @Test
    void recentListsTheNewestFirstAndCapsTheLimit() {
        RoleRuleBatch older = stage(TENANT, 1);
        clock.advanceMinutes(10);
        RoleRuleBatch newer = stage(TENANT, 1);
        for (int i = 0; i < OrgStructureConstants.MAX_PAGE_SIZE + 5; i++) {
            stage(OTHER, 1);
        }

        assertThat(store.recent(TENANT, 50)).extracting(RoleRuleBatch::id).containsExactly(newer.id(), older.id());
        assertThat(store.recent(OTHER, 100_000)).hasSize(OrgStructureConstants.MAX_PAGE_SIZE);
        assertThat(store.recent(TENANT, 0)).hasSize(1);
    }

    @Test
    void theLifeCycleStatesMoveOnlyAlongAllowedSteps() {
        RoleRuleBatch batch = stage(TENANT, 2);

        assertThat(store.cancel(OTHER, batch.id())).isFalse();
        assertThat(store.claimForApply(OTHER, batch.id())).isFalse();
        assertThat(store.find(OTHER, batch.id())).isEmpty();
        assertThat(store.claimForApply(TENANT, batch.id())).isTrue();
        assertThat(store.claimForApply(TENANT, batch.id())).isFalse();
        assertThat(store.cancel(TENANT, batch.id())).isFalse();
        store.finish(batch.id(), OrgStructureConstants.IMPORT_FAILED, "boom");
        assertThat(store.find(TENANT, batch.id()).orElseThrow()).satisfies(b -> {
            assertThat(b.status()).isEqualTo("FAILED");
            assertThat(b.message()).isEqualTo("boom");
            assertThat(b.appliedAt()).isNotNull();
        });
        assertThat(store.claimForApply(TENANT, batch.id())).isTrue();
    }

    @Test
    void stagedRowsAreReadInRowOrderAfterTheGivenRowAndMarkedAsAppliedWithTheirCounts() {
        RoleRuleBatch batch = stage(TENANT, 6);

        assertThat(store.rows(batch.id(), "VALID", 0, 2)).extracting(r -> r.input().rowNo()).containsExactly(1, 3);
        assertThat(store.rows(batch.id(), "VALID", 3, 10)).extracting(r -> r.input().rowNo()).containsExactly(5);

        store.markRows(batch.id(), List.of(1, 3), "APPLIED");
        store.addCounts(batch.id(), 2, 1);
        store.addCounts(batch.id(), 1, 0);

        assertThat(store.rows(batch.id(), "VALID", 0, 10)).extracting(r -> r.input().rowNo()).containsExactly(5);
        assertThat(store.rows(batch.id(), "APPLIED", 0, 10)).extracting(r -> r.input().rowNo()).containsExactly(1, 3);
        assertThat(store.find(TENANT, batch.id()).orElseThrow()).satisfies(b -> {
            assertThat(b.createdCount()).isEqualTo(3);
            assertThat(b.unchangedCount()).isEqualTo(1);
        });
    }

    @Test
    void theReferenceHoldsTheActiveDepartmentCodesAndEveryLadderWithItsPositionIdsOfTheOrganizationOnly() {
        unit("u-1", TENANT, "TECH", "ACTIVE");
        unit("u-2", TENANT, "OLD", "ARCHIVED");
        unit("u-3", OTHER, "THEIRS", "ACTIVE");
        ladder("l-1", TENANT, " Tech Ladder ");
        ladder("l-2", TENANT, "Empty");
        ladder("l-3", OTHER, "Theirs");
        position("p-1", TENANT, "l-1", "Senior Engineer", 4);
        position("p-2", OTHER, "l-3", "Clerk", 1);

        RoleRuleReference reference = store.loadReference(TENANT);

        assertThat(reference.activeUnitCodes()).containsExactly("TECH");
        assertThat(reference.positionIdsByLadder()).containsOnlyKeys("tech ladder", "empty");
        assertThat(reference.positionIdsByLadder().get("tech ladder")).containsExactly(Map.entry("senior engineer", "p-1"));
        assertThat(reference.positionIdsByLadder().get("empty")).isEmpty();
        assertThat(store.loadReference("t-3").activeUnitCodes()).isEmpty();
    }

    @Test
    void departmentsAndPositionsAreFoundWithinTheOrganizationOnly() {
        unit("u-1", TENANT, "TECH", "ACTIVE");
        unit("u-2", TENANT, "OLD", "ARCHIVED");
        unit("u-3", OTHER, "TECH", "ACTIVE");
        ladder("l-1", TENANT, "Tech Ladder");
        ladder("l-2", OTHER, "Tech Ladder");
        position("p-1", TENANT, "l-1", "Lead", 6);
        position("p-2", OTHER, "l-2", "Lead", 6);

        assertThat(store.findUnitId(TENANT, "TECH")).contains("u-1");
        assertThat(store.findUnitId(OTHER, "TECH")).contains("u-3");
        assertThat(store.findUnitId(TENANT, "OLD")).as("an archived department cannot get a rule").isEmpty();
        assertThat(store.findUnitId(TENANT, "tech")).isEmpty();
        assertThat(store.findPositionId(TENANT, " tech LADDER ", " LEAD ")).contains("p-1");
        assertThat(store.findPositionId(OTHER, "Tech Ladder", "Lead")).contains("p-2");
        assertThat(store.findPositionId(TENANT, "Tech Ladder", "Nobody")).isEmpty();
        assertThat(store.findPositionId("t-3", "Tech Ladder", "Lead")).isEmpty();
    }
}
