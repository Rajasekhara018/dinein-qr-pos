package com.heuristiq.identity.orgstructure.adapter.out;

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

import com.heuristiq.identity.orgstructure.domain.LadderImportModels.ExistingPosition;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderBatch;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderReference;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRow;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRowInput;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.StagedLadderRow;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;

/** The ladders import store on H2 with the project's own migration scripts: paging caps, lookups by name, tenant filters, the guard query. */
class JdbcLadderImportStoreTest {
    private static final String TENANT = "t-1";
    private static final String OTHER = "t-2";

    private NamedParameterJdbcTemplate jdbc;
    private JdbcLadderImportStore store;
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
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:ladderstore_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V8__org_import.sql"),
                new ClassPathResource("db/migration/V21__org_ladder_import_and_heads.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new JdbcLadderImportStore(jdbc, clock);
    }

    private static LadderRow row(int no, boolean valid) {
        return new LadderRow(new LadderRowInput(no, "Tech", "Position " + no, "3", "yes"),
                valid ? OrgStructureConstants.IMPORT_ROW_VALID : OrgStructureConstants.IMPORT_ROW_INVALID, valid ? List.of() : List.of("X" + no));
    }

    private LadderBatch stage(String tenant, int rows) {
        List<LadderRow> list = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            list.add(row(i, i % 2 == 1));
        }
        return store.stage(tenant, "actor", "doc-" + UUID.randomUUID(), "f.csv", list);
    }

    private void ladder(String id, String tenant, String name) {
        jdbc.update("insert into org_ladder (id, tenant_id, name, created_at) values (:id, :t, :n, current_timestamp)",
                Map.of("id", id, "t", tenant, "n", name));
    }

    private void position(String id, String tenant, String ladderId, String name, int rank, boolean managerial) {
        jdbc.update("insert into org_position (id, tenant_id, ladder_id, name, rank_no, managerial) values (:id, :t, :l, :n, :r, :m)",
                Map.of("id", id, "t", tenant, "l", ladderId, "n", name, "r", rank, "m", managerial));
    }

    @Test
    void stagingRecordsTheCountsTheRowsAsTypedAndWhoStartedIt() {
        LadderBatch batch = stage(TENANT, 5);

        assertThat(batch.status()).isEqualTo("VALIDATED");
        assertThat(batch.totalRows()).isEqualTo(5);
        assertThat(batch.validRows()).isEqualTo(3);
        assertThat(batch.errorRows()).isEqualTo(2);
        assertThat(batch.laddersCreated()).isZero();
        assertThat(batch.createdBy()).isEqualTo("actor");
        assertThat(batch.fileName()).isEqualTo("f.csv");
        assertThat(batch.createdAt()).isEqualTo(clock.instant());
        assertThat(batch.appliedAt()).isNull();
        assertThat(store.rows(batch.id(), "VALID", 0, 1).get(0).input()).isEqualTo(new LadderRowInput(1, "Tech", "Position 1", "3", "yes"));
    }

    @Test
    void aFileLargerThanOneInsertChunkIsStagedWhole() {
        int rows = OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK + 250;

        LadderBatch batch = stage(TENANT, rows);

        assertThat(jdbc.queryForObject("select count(*) from org_ladder_import_row where import_id = :id", Map.of("id", batch.id()), Integer.class))
                .isEqualTo(rows);
    }

    @Test
    void theRejectedRowsPageByRowNumberAndTheLimitIsCapped() {
        LadderBatch batch = stage(TENANT, 3 * OrgStructureConstants.MAX_PAGE_SIZE + 10);

        List<LadderRow> first = store.errors(TENANT, batch.id(), 0, 10_000);

        assertThat(first).hasSize(OrgStructureConstants.MAX_PAGE_SIZE);
        assertThat(first).allMatch(r -> !r.valid());
        assertThat(first.get(0).input().rowNo()).isEqualTo(2);
        int last = first.get(first.size() - 1).input().rowNo();
        assertThat(store.errors(TENANT, batch.id(), last, 3).get(0).input().rowNo()).isGreaterThan(last);
        assertThat(store.errors(TENANT, batch.id(), 0, 0)).as("a limit below one is lifted to one").hasSize(1);
        assertThat(store.errors(TENANT, batch.id(), 0, 5).get(0).errors()).containsExactly("X2");
        assertThat(store.errors(OTHER, batch.id(), 0, 5)).as("another organization sees no rows").isEmpty();
    }

    @Test
    void recentListsTheNewestFirstAndCapsTheLimit() {
        LadderBatch older = stage(TENANT, 1);
        clock.advanceMinutes(10);
        LadderBatch newer = stage(TENANT, 1);
        for (int i = 0; i < OrgStructureConstants.MAX_PAGE_SIZE + 5; i++) {
            stage(OTHER, 1);
        }

        assertThat(store.recent(TENANT, 50)).extracting(LadderBatch::id).containsExactly(newer.id(), older.id());
        assertThat(store.recent(OTHER, 100_000)).hasSize(OrgStructureConstants.MAX_PAGE_SIZE);
        assertThat(store.recent(TENANT, 0)).hasSize(1);
    }

    @Test
    void theLifeCycleStatesMoveOnlyAlongAllowedSteps() {
        LadderBatch batch = stage(TENANT, 2);

        assertThat(store.cancel(OTHER, batch.id())).isFalse();
        assertThat(store.claimForApply(OTHER, batch.id())).isFalse();
        assertThat(store.find(OTHER, batch.id())).isEmpty();
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
    void stagedRowsAreReadInRowOrderAfterTheGivenRowAndMarkedAsAppliedWithTheirCounts() {
        LadderBatch batch = stage(TENANT, 6);

        List<StagedLadderRow> valid = store.rows(batch.id(), "VALID", 0, 2);
        assertThat(valid).extracting(r -> r.input().rowNo()).containsExactly(1, 3);
        assertThat(store.rows(batch.id(), "VALID", 3, 10)).extracting(r -> r.input().rowNo()).containsExactly(5);

        store.markRows(batch.id(), List.of(1, 3), "APPLIED");
        store.addCounts(batch.id(), 2, 1, 1);
        store.addCounts(batch.id(), 1, 0, 2);

        assertThat(store.rows(batch.id(), "VALID", 0, 10)).extracting(r -> r.input().rowNo()).containsExactly(5);
        assertThat(store.rows(batch.id(), "APPLIED", 0, 10)).extracting(r -> r.input().rowNo()).containsExactly(1, 3);
        assertThat(store.find(TENANT, batch.id()).orElseThrow()).satisfies(b -> {
            assertThat(b.createdCount()).isEqualTo(3);
            assertThat(b.updatedCount()).isEqualTo(1);
            assertThat(b.laddersCreated()).isEqualTo(3);
        });
    }

    @Test
    void theReferenceHoldsEveryLadderOfTheOrganizationWithItsPositionsByLowerCaseName() {
        ladder("l-tech", TENANT, " Tech Ladder ");
        ladder("l-empty", TENANT, "Empty");
        ladder("l-other", OTHER, "Theirs");
        position("p-1", TENANT, "l-tech", "Senior Engineer", 4, true);
        position("p-2", TENANT, "l-tech", "Engineer", 3, false);
        position("p-3", OTHER, "l-other", "Clerk", 1, false);

        LadderReference reference = store.loadReference(TENANT);

        assertThat(reference.positionsByLadder()).containsOnlyKeys("tech ladder", "empty");
        assertThat(reference.positionsByLadder().get("empty")).isEmpty();
        assertThat(reference.positionsByLadder().get("tech ladder")).containsOnlyKeys("senior engineer", "engineer");
        assertThat(reference.positionsByLadder().get("tech ladder").get("senior engineer"))
                .isEqualTo(new ExistingPosition("p-1", "Senior Engineer", 4, true));
        assertThat(store.loadReference("t-3").positionsByLadder()).isEmpty();
    }

    @Test
    void laddersAndPositionsAreFoundByNameIgnoringCaseAndBlanksWithinTheOrganizationOnly() {
        ladder("l-1", TENANT, "Tech Ladder");
        ladder("l-2", OTHER, "Tech Ladder");
        position("p-1", TENANT, "l-1", "Lead", 6, true);
        position("p-2", OTHER, "l-2", "Lead", 7, false);

        assertThat(store.findLadderId(TENANT, "  tech LADDER ")).contains("l-1");
        assertThat(store.findLadderId(OTHER, "Tech Ladder")).contains("l-2");
        assertThat(store.findLadderId(TENANT, "Other")).isEmpty();
        assertThat(store.findPosition(TENANT, "l-1", " LEAD ")).contains(new ExistingPosition("p-1", "Lead", 6, true));
        assertThat(store.findPosition(TENANT, "l-1", "Nobody")).isEmpty();
        assertThat(store.findPosition(TENANT, "l-2", "Lead")).as("a ladder of another organization").isEmpty();
    }

    @Test
    void theRollbackGuardSeesOnlyAppliedImportsOfTheSameOrganizationAfterTheGivenTime() {
        Instant before = clock.instant();
        LadderBatch applied = stage(TENANT, 2);
        LadderBatch failed = stage(TENANT, 2);
        LadderBatch otherOrg = stage(OTHER, 2);
        clock.advanceMinutes(5);
        store.finish(applied.id(), OrgStructureConstants.IMPORT_APPLIED, null);
        store.finish(failed.id(), OrgStructureConstants.IMPORT_FAILED, "x");
        store.finish(otherOrg.id(), OrgStructureConstants.IMPORT_APPLIED, null);

        assertThat(store.anyAppliedSince(TENANT, before)).isTrue();
        assertThat(store.anyAppliedSince(TENANT, clock.instant())).as("not after a time that is not before it").isFalse();
        assertThat(store.anyAppliedSince("t-3", before)).isFalse();
    }
}
