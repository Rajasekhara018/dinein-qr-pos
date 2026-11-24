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
import java.util.Set;
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

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.uam.orgstructure.application.OrgLoginService;
import com.heuristiq.uam.orgstructure.application.RoleRuleImportService;
import com.heuristiq.uam.orgstructure.domain.ChangeType;
import com.heuristiq.uam.orgstructure.domain.EmployeeStore;
import com.heuristiq.uam.orgstructure.domain.ImportLimits;
import com.heuristiq.uam.orgstructure.domain.LoginAccounts;
import com.heuristiq.uam.orgstructure.domain.Models.Change;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.OrgStructureStore;
import com.heuristiq.uam.orgstructure.domain.RoleRuleFileSource;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportAudit;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleBatch;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleFile;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportStore;
import com.heuristiq.uam.orgstructure.domain.RoleRuleStore;

/**
 * The roles import end to end on H2 with the project's own migration scripts: a file is validated and staged without touching the
 * organization, only a confirmed batch is applied (through the logins screen's own createRule), existing rules are counted as unchanged
 * and never changed, a failed batch resumes, and one organization never sees another's import.
 */
class RoleRuleImportServiceTest {
    private static final String TENANT = "tenant-1";
    private static final String OTHER = "tenant-2";
    private static final String ACTOR = "admin";
    private static final LocalDate TODAY = LocalDate.parse("2026-10-13");

    private OrgStructureStore structure;
    private RoleRuleImportService service;
    private RoleRuleImportStore store;
    private OrgLoginService logins;
    private NamedParameterJdbcTemplate jdbc;
    private final Map<String, List<RoleRuleRowInput>> documents = new HashMap<>();
    private final List<String> audits = new ArrayList<>();
    private final HeldExecutor executor = new HeldExecutor();
    private final Set<String> roles = new java.util.HashSet<>(Set.of("EMPLOYEE", "TEAM_LEAD"));
    private final Map<String, Set<String>> rolesOfOrganization = new HashMap<>();
    private RuntimeException fileSourceFailure;

    /** Role codes per organization, the only part of the accounts feature the roles import touches. */
    private final class FakeAccounts implements LoginAccounts {
        @Override public Set<String> roleCodes(String tenantId) { return rolesOfOrganization.getOrDefault(tenantId, roles); }
        @Override public boolean emailInUse(String email) { return false; }
        @Override public String createLogin(String tenantId, String email, String fullName, String phone, List<String> roleCodes) {
            throw new UnsupportedOperationException("the roles import never creates logins");
        }
    }

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
        String url = "jdbc:h2:mem:ruleimport_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V8__org_import.sql"),
                new ClassPathResource("db/migration/V9__org_role_rule.sql"),
                new ClassPathResource("db/migration/V22__org_role_rule_import.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        DataSourceTransactionManager tx = new DataSourceTransactionManager(dataSource);
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
        structure = (OrgStructureStore) construct("JdbcOrgStructureStore", jdbc, clock);
        store = (RoleRuleImportStore) construct("JdbcRoleRuleImportStore", jdbc, clock);
        RoleRuleStore ruleStore = (RoleRuleStore) construct("JdbcRoleRuleStore", jdbc, clock);
        EmployeeStore employees = (EmployeeStore) construct("JdbcEmployeeStore", jdbc, clock);
        LoginAccounts accounts = new FakeAccounts();
        logins = new OrgLoginService(ruleStore, employees, accounts);
        RoleRuleFileSource files = (documentId, callerId) -> {
            if (fileSourceFailure != null) {
                throw fileSourceFailure;
            }
            List<RoleRuleRowInput> rows = documents.get(documentId);
            if (rows == null) {
                throw OrgStructureException.notFound("The uploaded file was not found: " + documentId);
            }
            return new RoleRuleFile(documentId, documentId + ".csv", rows);
        };
        RoleRuleImportAudit audit = (tenant, actor, batch, success, file, total, valid, created, unchanged, message) ->
                audits.add(tenant + "|" + actor + "|" + success + "|" + total + "|" + valid + "|" + created + "|" + unchanged + "|" + message);
        service = new RoleRuleImportService(store, files, logins, accounts, new ImportLimits(1000, 2), executor, tx, audit);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Object construct(String simpleName, Object... args) throws Exception {
        var constructor = Class.forName("com.heuristiq.uam.orgstructure.adapter.out." + simpleName).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static RoleRuleRowInput row(int no, String dept, String ladder, String position, String role) {
        return new RoleRuleRowInput(no, dept, ladder, position, role);
    }

    private RoleRuleBatch upload(String tenant, List<RoleRuleRowInput> rows) {
        String documentId = "doc-" + documents.size();
        documents.put(documentId, rows);
        return service.upload(tenant, ACTOR, documentId);
    }

    private RoleRuleBatch fresh(String tenant, String batchId) {
        return service.batch(tenant, batchId);
    }

    private void unit(String tenant, String code) {
        structure.apply(tenant, new Change(null, null, 0, ChangeType.UNIT_CREATE,
                Map.of("id", UUID.randomUUID().toString(), "code", code, "name", code)), TODAY);
    }

    private String ladderWithPosition(String tenant, String ladderName, String positionName) {
        String ladderId = UUID.randomUUID().toString();
        structure.apply(tenant, new Change(null, null, 0, ChangeType.LADDER_CREATE, Map.of("id", ladderId, "name", ladderName)), TODAY);
        structure.apply(tenant, new Change(null, null, 0, ChangeType.POSITION_UPSERT,
                Map.of("id", UUID.randomUUID().toString(), "ladderId", ladderId, "name", positionName, "rank", 3, "managerial", false)), TODAY);
        return ladderId;
    }

    private int rules(String tenant) {
        Integer n = jdbc.queryForObject("select count(*) from org_role_rule where tenant_id = :t", Map.of("t", tenant), Integer.class);
        return n == null ? 0 : n;
    }

    private void organization() {
        unit(TENANT, "TECH");
        unit(TENANT, "HR");
        ladderWithPosition(TENANT, "Tech", "Engineer");
    }

    // ---- upload: validate and stage, change nothing ---------------------------------------------------

    @Test
    void uploadValidatesEveryRowAndAddsNoRule() {
        organization();

        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "TECH", null, null, "EMPLOYEE"), row(2, "NOPE", null, null, "EMPLOYEE"),
                row(3, null, "Tech", "Engineer", "BOSS"), row(4, "TECH", null, null, "EMPLOYEE")));

        assertThat(batch.status()).isEqualTo(OrgStructureConstants.IMPORT_VALIDATED);
        assertThat(batch.totalRows()).isEqualTo(4);
        assertThat(batch.validRows()).isEqualTo(1);
        assertThat(batch.errorRows()).isEqualTo(3);
        assertThat(rules(TENANT)).isZero();
        assertThat(service.errors(TENANT, batch.id(), 0, 50)).extracting(r -> r.errors().get(0))
                .containsExactly("DEPARTMENT_UNKNOWN:NOPE", "ROLE_UNKNOWN:BOSS", "DUPLICATE_ROW:1");
    }

    @Test
    void aFileWithNoRowsOrTooManyRowsIsRefused() {
        assertThatThrownBy(() -> upload(TENANT, List.of())).isInstanceOf(OrgStructureException.class).hasMessage("The file has no data rows");
        List<RoleRuleRowInput> tooMany = new ArrayList<>();
        for (int i = 1; i <= 1001; i++) {
            tooMany.add(row(i, "TECH", null, null, "EMPLOYEE"));
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

    @Test
    void aRoleOnlyAnotherOrganizationOwnsIsUnknownToThisOne() {
        organization();
        rolesOfOrganization.put(OTHER, Set.of("EMPLOYEE", "THEIR_ROLE"));

        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "TECH", null, null, "THEIR_ROLE")));

        assertThat(batch.validRows()).isZero();
        assertThat(service.errors(TENANT, batch.id(), 0, 10).get(0).errors()).containsExactly("ROLE_UNKNOWN:THEIR_ROLE");
    }

    // ---- confirm: apply -------------------------------------------------------------------------------

    @Test
    void confirmAddsTheRulesForDepartmentPositionAndBothSkippingRejectedRows() {
        organization();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "TECH", null, null, "employee"), row(2, null, "tech", "ENGINEER", "TEAM_LEAD"),
                row(3, "HR", "Tech", "Engineer", "EMPLOYEE"), row(4, "NOPE", null, null, "EMPLOYEE")));

        service.confirm(TENANT, ACTOR, batch.id());

        RoleRuleBatch done = fresh(TENANT, batch.id());
        assertThat(done.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(done.createdCount()).isEqualTo(3);
        assertThat(done.unchangedCount()).isZero();
        assertThat(rules(TENANT)).isEqualTo(3);
        assertThat(logins.rules(TENANT)).extracting(r -> r.roleCode() + "@" + (r.unitName() == null ? "-" : r.unitName()) + "/"
                + (r.positionName() == null ? "-" : r.positionName()))
                .containsExactlyInAnyOrder("EMPLOYEE@TECH/-", "TEAM_LEAD@-/Engineer", "EMPLOYEE@HR/Engineer");
        assertThat(audits).containsExactly(TENANT + "|admin|true|4|3|3|0|null");
    }

    @Test
    void aRuleThatAlreadyExistsIsCountedUnchangedAndNothingIsDuplicatedOrDeleted() {
        organization();
        String hr = jdbc.queryForObject("select id from org_unit where code = 'HR'", Map.of(), String.class);
        logins.createRule(TENANT, ACTOR, hr, null, "EMPLOYEE");
        String untouched = logins.createRule(TENANT, ACTOR, hr, null, "TEAM_LEAD").id();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "HR", null, null, "EMPLOYEE"), row(2, "TECH", null, null, "EMPLOYEE")));

        service.confirm(TENANT, ACTOR, batch.id());

        RoleRuleBatch done = fresh(TENANT, batch.id());
        assertThat(done.createdCount()).isEqualTo(1);
        assertThat(done.unchangedCount()).isEqualTo(1);
        assertThat(rules(TENANT)).isEqualTo(3);
        assertThat(logins.rules(TENANT)).extracting(r -> r.id()).contains(untouched);
    }

    @Test
    void runningTheSameFileAgainAddsNothing() {
        organization();
        List<RoleRuleRowInput> file = List.of(row(1, "TECH", null, null, "EMPLOYEE"), row(2, null, "Tech", "Engineer", "TEAM_LEAD"));
        service.confirm(TENANT, ACTOR, upload(TENANT, file).id());

        RoleRuleBatch second = upload(TENANT, file);
        service.confirm(TENANT, ACTOR, second.id());

        RoleRuleBatch done = fresh(TENANT, second.id());
        assertThat(done.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(done.createdCount()).isZero();
        assertThat(done.unchangedCount()).isEqualTo(2);
        assertThat(rules(TENANT)).isEqualTo(2);
    }

    // ---- life cycle ---------------------------------------------------------------------------------------

    @Test
    void aCancelledImportChangesNothingAndCannotBeConfirmedOrCancelledAgain() {
        organization();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "TECH", null, null, "EMPLOYEE")));

        service.cancel(TENANT, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_CANCELLED);
        assertThat(rules(TENANT)).isZero();
        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class).hasMessageContaining("cannot be confirmed");
        assertThatThrownBy(() -> service.cancel(TENANT, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("Only a validated import can be cancelled");
    }

    @Test
    void aSecondConfirmWhileTheFirstIsApplyingIsRefusedAndTheWorkRunsOnlyOnce() {
        organization();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "TECH", null, null, "EMPLOYEE")));
        executor.hold = true;

        RoleRuleBatch first = service.confirm(TENANT, ACTOR, batch.id());

        assertThat(first.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLYING);
        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class).hasMessageContaining("APPLYING");
        assertThat(executor.held).hasSize(1);
        executor.runHeld();
        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(fresh(TENANT, batch.id()).createdCount()).isEqualTo(1);
        assertThat(rules(TENANT)).isEqualTo(1);
    }

    @Test
    void aFullQueueEndsTheBatchFailedSoItCanBeConfirmedAgainLater() {
        organization();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "TECH", null, null, "EMPLOYEE")));
        executor.reject = true;

        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("Another import is running");
        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        executor.reject = false;

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(rules(TENANT)).isEqualTo(1);
    }

    @Test
    void anImportWithNoValidRowCannotBeConfirmed() {
        organization();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "NOPE", null, null, "EMPLOYEE")));

        assertThatThrownBy(() -> service.confirm(TENANT, ACTOR, batch.id())).isInstanceOf(OrgStructureException.class)
                .hasMessage("There are no valid rows to import");
    }

    @Test
    void aRoleRemovedBetweenTheCheckAndTheConfirmFailsTheBatchAndConfirmingAgainResumesWhereItStopped() {
        organization();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "TECH", null, null, "EMPLOYEE"), row(2, "HR", null, null, "EMPLOYEE"),
                row(3, "TECH", null, null, "TEAM_LEAD"), row(4, "HR", null, null, "TEAM_LEAD")));
        roles.remove("TEAM_LEAD");   // the role disappears before the confirm; rows 1-2 are the first chunk (chunk size 2)

        service.confirm(TENANT, ACTOR, batch.id());

        RoleRuleBatch failed = fresh(TENANT, batch.id());
        assertThat(failed.status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        assertThat(failed.message()).startsWith("Row 3:").contains("Unknown role");
        assertThat(failed.createdCount()).as("the first chunk was committed before the failing one").isEqualTo(2);
        assertThat(audits.get(0)).contains("|false|").contains("Row 3");
        roles.add("TEAM_LEAD");

        service.confirm(TENANT, ACTOR, batch.id());

        RoleRuleBatch resumed = fresh(TENANT, batch.id());
        assertThat(resumed.status()).isEqualTo(OrgStructureConstants.IMPORT_APPLIED);
        assertThat(resumed.createdCount()).isEqualTo(4);
        assertThat(rules(TENANT)).isEqualTo(4);
    }

    @Test
    void aDepartmentArchivedBetweenTheCheckAndTheConfirmFailsTheBatchWithAClearReason() {
        organization();
        RoleRuleBatch batch = upload(TENANT, List.of(row(1, "HR", null, null, "EMPLOYEE")));
        structure.apply(TENANT, new Change(null, null, 0, ChangeType.UNIT_ARCHIVE,
                Map.of("id", jdbc.queryForObject("select id from org_unit where code = 'HR'", Map.of(), String.class))), TODAY);

        service.confirm(TENANT, ACTOR, batch.id());

        assertThat(fresh(TENANT, batch.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_FAILED);
        assertThat(fresh(TENANT, batch.id()).message()).contains("Row 1: Unknown department HR");
    }

    // ---- organizations ---------------------------------------------------------------------------------

    @Test
    void oneOrganizationCannotSeeConfirmCancelOrListAnotherOnesImport() {
        organization();
        RoleRuleBatch mine = upload(TENANT, List.of(row(1, "NOPE", null, null, "EMPLOYEE"), row(2, "TECH", null, null, "EMPLOYEE")));

        assertThatThrownBy(() -> service.batch(OTHER, mine.id())).isInstanceOf(OrgStructureException.class)
                .extracting(e -> ((OrgStructureException) e).kind()).isEqualTo(OrgStructureException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> service.errors(OTHER, mine.id(), 0, 50)).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> service.confirm(OTHER, ACTOR, mine.id())).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> service.cancel(OTHER, mine.id())).isInstanceOf(OrgStructureException.class);
        assertThat(service.recent(OTHER, 50)).isEmpty();
        assertThat(store.errors(OTHER, mine.id(), 0, 50)).isEmpty();
        assertThat(store.claimForApply(OTHER, mine.id())).isFalse();
        assertThat(store.cancel(OTHER, mine.id())).isFalse();
        assertThat(service.recent(TENANT, 50)).extracting(RoleRuleBatch::id).containsExactly(mine.id());
        assertThat(fresh(TENANT, mine.id()).status()).isEqualTo(OrgStructureConstants.IMPORT_VALIDATED);
    }

    @Test
    void aFileIsCheckedAgainstAndAppliedToTheCallersOrganizationOnly() {
        organization();
        unit(OTHER, "ONLY_THEIRS");
        unit(OTHER, "TECH");

        RoleRuleBatch batch = upload(OTHER, List.of(row(1, "TECH", null, null, "EMPLOYEE"), row(2, "HR", null, null, "EMPLOYEE"),
                row(3, null, "Tech", "Engineer", "EMPLOYEE")));

        assertThat(batch.validRows()).as("HR, the Tech ladder and its positions only exist in the first organization").isEqualTo(1);
        service.confirm(OTHER, ACTOR, batch.id());
        assertThat(rules(OTHER)).isEqualTo(1);
        assertThat(rules(TENANT)).as("the other organization is untouched").isZero();
        assertThat(logins.rules(OTHER)).singleElement().satisfies(r -> assertThat(r.unitName()).isEqualTo("TECH"));
    }

    @Test
    void theSameRuleInTwoOrganizationsIsTwoRules() {
        organization();
        unit(OTHER, "TECH");

        service.confirm(TENANT, ACTOR, upload(TENANT, List.of(row(1, "TECH", null, null, "EMPLOYEE"))).id());
        service.confirm(OTHER, ACTOR, upload(OTHER, List.of(row(1, "TECH", null, null, "EMPLOYEE"))).id());

        assertThat(rules(TENANT)).isEqualTo(1);
        assertThat(rules(OTHER)).isEqualTo(1);
    }
}
