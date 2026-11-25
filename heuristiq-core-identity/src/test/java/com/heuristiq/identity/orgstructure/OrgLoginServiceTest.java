package com.heuristiq.identity.orgstructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.orgstructure.application.OrgLoginService;
import com.heuristiq.identity.orgstructure.application.OrgStructureService;
import com.heuristiq.identity.orgstructure.domain.ChangeSetStore;
import com.heuristiq.identity.orgstructure.domain.ChangeType;
import com.heuristiq.identity.orgstructure.domain.EmployeeStore;
import com.heuristiq.identity.orgstructure.domain.LoginAccounts;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginCandidate;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginOutcome;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginRun;
import com.heuristiq.identity.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.identity.orgstructure.domain.Models.Employee;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;
import com.heuristiq.identity.orgstructure.domain.RoleRuleStore;

/**
 * Role-mapping rules and login creation end to end on H2, with a fake account directory standing in for UAM's users.
 * Organization: Technical (ladder TL/SE) with a sub-department Testing, plus HR with no rules.
 */
class OrgLoginServiceTest {
    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "admin";

    private OrgStructureService structure;
    private OrgLoginService logins;
    private EmployeeStore employees;
    private NamedParameterJdbcTemplate jdbc;
    private FakeAccounts accounts;
    private String tech;
    private String testing;
    private String hr;
    private String positionTl;
    private String positionSe;

    /** The user directory: some roles exist, some emails are taken, and creation can be made to fail. */
    private static final class FakeAccounts implements LoginAccounts {
        final Set<String> roles = new HashSet<>(Set.of("EMPLOYEE", "TEAM_LEAD"));
        final Set<String> takenEmails = new HashSet<>();
        final List<String> created = new ArrayList<>();
        final List<List<String>> rolesGiven = new ArrayList<>();
        boolean failCreation;

        @Override public Set<String> roleCodes(String tenantId) { return roles; }
        @Override public boolean emailInUse(String email) { return takenEmails.contains(email.toLowerCase()); }

        @Override
        public String createLogin(String tenantId, String email, String fullName, String phone, List<String> roleCodes) {
            if (failCreation) {
                throw OrgStructureException.conflict("directory unavailable");
            }
            takenEmails.add(email.toLowerCase());
            created.add(email);
            rolesGiven.add(roleCodes);
            return "user-" + created.size();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:orglogin_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V9__org_role_rule.sql"),
                new ClassPathResource("db/migration/V8__org_import.sql"),
                new ClassPathResource("db/migration/V21__org_ladder_import_and_heads.sql"),
                new ClassPathResource("db/migration/V10__org_publish_snapshot.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
        var tx = new DataSourceTransactionManager(dataSource);

        OrgStructureStore store = (OrgStructureStore) construct("JdbcOrgStructureStore", jdbc, clock);
        ChangeSetStore changes = (ChangeSetStore) construct("JdbcChangeSetStore", jdbc, new ObjectMapper(), clock);
        employees = (EmployeeStore) construct("JdbcEmployeeStore", jdbc, clock);
        RoleRuleStore ruleStore = (RoleRuleStore) construct("JdbcRoleRuleStore", jdbc, clock);
        accounts = new FakeAccounts();
        var snapshots = (com.heuristiq.identity.orgstructure.domain.StructureSnapshotStore) construct("JdbcStructureSnapshotStore", jdbc, clock);
        var imports = (com.heuristiq.identity.orgstructure.domain.ImportStore) construct("JdbcImportStore", jdbc, clock);
        structure = new OrgStructureService(store, changes, employees, snapshots, imports, tx, clock);
        logins = new OrgLoginService(ruleStore, employees, accounts);
        createOrganization();
    }

    private void createOrganization() {
        ChangeSet draft = structure.openDraft(TENANT, ACTOR, null);
        String ladder = add(draft, ChangeType.LADDER_CREATE, Map.of("name", "Tech"));
        positionTl = add(draft, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "TL", "rank", 3));
        positionSe = add(draft, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        tech = add(draft, ChangeType.UNIT_CREATE, Map.of("code", "TECH", "name", "Technical", "ladderId", ladder));
        testing = add(draft, ChangeType.UNIT_CREATE, Map.of("code", "TEST", "name", "Testing", "parentId", tech, "ladderId", ladder));
        hr = add(draft, ChangeType.UNIT_CREATE, Map.of("code", "HR", "name", "Human Resources", "ladderId", ladder));
        assertThat(structure.publish(TENANT, draft.id(), ACTOR).applied()).isTrue();
    }

    private String add(ChangeSet draft, ChangeType type, Map<String, Object> payload) {
        return String.valueOf(structure.addChange(TENANT, draft.id(), type, payload).payload().get("id"));
    }

    /** Creates an employee and places them (in one published draft). */
    private Employee person(String code, String email, String unit, String position) {
        Employee e = structure.createEmployee(TENANT, new Employee(null, code, "Person " + code, email, null, null, null, null));
        ChangeSet draft = structure.openDraft(TENANT, ACTOR, null);
        add(draft, ChangeType.ASSIGN, Map.of("employeeId", e.id(), "unitId", unit, "positionId", position));
        assertThat(structure.publish(TENANT, draft.id(), ACTOR).applied()).isTrue();
        return e;
    }

    private void standardRules() {
        logins.createRule(TENANT, ACTOR, tech, null, "employee");
        logins.createRule(TENANT, ACTOR, null, positionTl, "team_lead");
    }

    @Test
    void previewShowsRolesFromTheRulesAndWhatBlocksEachLogin() {
        standardRules();
        person("E1", "e1@x.com", tech, positionTl);
        person("E2", "e2@x.com", testing, positionSe);
        person("E3", null, testing, positionSe);
        person("E4", "e4@x.com", hr, positionSe);

        List<LoginCandidate> preview = logins.candidates(TENANT, null, null, 50);

        assertThat(preview).hasSize(4);
        assertThat(find(preview, "E1").roles()).containsExactly("EMPLOYEE", "TEAM_LEAD");
        assertThat(find(preview, "E1").ready()).isTrue();
        assertThat(find(preview, "E2").roles()).as("the Technical rule covers Testing below it").containsExactly("EMPLOYEE");
        assertThat(find(preview, "E3").issues()).containsExactly("NO_EMAIL");
        assertThat(find(preview, "E4").issues()).as("no rule reaches HR").containsExactly("NO_ROLE_RULE");
        assertThat(accounts.created).as("a preview creates nothing").isEmpty();
    }

    @Test
    void previewCanBeLimitedToADepartmentAndEverythingBelowIt() {
        standardRules();
        person("E1", "e1@x.com", tech, positionTl);
        person("E2", "e2@x.com", testing, positionSe);
        person("E4", "e4@x.com", hr, positionSe);

        assertThat(logins.candidates(TENANT, tech, null, 50)).extracting(c -> c.candidate().employeeCode())
                .containsExactlyInAnyOrder("E1", "E2");
        assertThat(logins.candidates(TENANT, testing, null, 50)).extracting(c -> c.candidate().employeeCode()).containsExactly("E2");
    }

    @Test
    void createLoginsMakesAccountsWithTheRuleRolesLinksThemAndReportsTheRest() {
        standardRules();
        Employee e1 = person("E1", "e1@x.com", tech, positionTl);
        person("E3", null, testing, positionSe);
        person("E4", "e4@x.com", hr, positionSe);

        LoginRun run = logins.createLogins(TENANT, ACTOR, null, List.of(), null, 50);

        assertThat(run.created()).isEqualTo(1);
        assertThat(run.skipped()).isEqualTo(2);
        assertThat(run.failed()).isZero();
        assertThat(accounts.created).containsExactly("e1@x.com");
        assertThat(accounts.rolesGiven.get(0)).containsExactly("EMPLOYEE", "TEAM_LEAD");
        assertThat(employees.find(TENANT, e1.id()).orElseThrow().userId()).isEqualTo("user-1");
        assertThat(run.results()).filteredOn(r -> r.outcome() == LoginOutcome.SKIPPED).extracting("code")
                .containsExactlyInAnyOrder("NO_EMAIL", "NO_ROLE_RULE");
    }

    @Test
    void runningItAgainDoesNotCreateSecondAccounts() {
        standardRules();
        person("E1", "e1@x.com", tech, positionTl);
        logins.createLogins(TENANT, ACTOR, null, List.of(), null, 50);
        LoginRun second = logins.createLogins(TENANT, ACTOR, null, List.of(), null, 50);

        assertThat(second.created()).isZero();
        assertThat(second.results()).isEmpty();
        assertThat(accounts.created).hasSize(1);
    }

    @Test
    void oneRequestNeverCreatesMoreThanTheBatchCapSoItCannotOutliveTheProxyTimeout() {
        standardRules();
        int total = OrgStructureConstants.MAX_LOGIN_BATCH + 5;
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            ids.add(person("B" + i, "b" + i + "@x.com", tech, positionSe).id());
        }

        LoginRun byCursor = logins.createLogins(TENANT, ACTOR, null, List.of(), null, 500);
        assertThat(byCursor.created()).isEqualTo(OrgStructureConstants.MAX_LOGIN_BATCH);
        assertThat(byCursor.nextAfterEmployeeId()).as("the caller continues from the cursor").isNotNull();

        LoginRun byIds = logins.createLogins(TENANT, ACTOR, null, ids, null, 500);
        assertThat(byIds.results()).hasSizeLessThanOrEqualTo(OrgStructureConstants.MAX_LOGIN_BATCH);
    }

    @Test
    void aPageOfPeopleWhoCannotGetLoginsDoesNotBlockTheOnesAfterThem() {
        standardRules();
        for (int i = 1; i <= 3; i++) {
            person("N" + i, null, tech, positionSe);            // no email: never eligible
        }
        person("OK", "ok@x.com", tech, positionSe);

        List<String> order = logins.candidates(TENANT, tech, null, 50).stream().map(c -> c.candidate().employeeId()).sorted().toList();
        LoginRun first = logins.createLogins(TENANT, ACTOR, tech, List.of(), null, 2);
        assertThat(first.nextAfterEmployeeId()).as("a full page says where to continue").isNotNull();

        String cursor = first.nextAfterEmployeeId();
        int created = first.created();
        for (int guard = 0; cursor != null && guard < 10; guard++) {
            LoginRun next = logins.createLogins(TENANT, ACTOR, tech, List.of(), cursor, 2);
            created += next.created();
            cursor = next.nextAfterEmployeeId();
        }
        assertThat(order).hasSize(4);
        assertThat(created).as("the eligible person is reached whatever order they sit in").isEqualTo(1);
        assertThat(accounts.created).containsExactly("ok@x.com");
    }

    @Test
    void anEmailAlreadyInUseIsSkippedNotOverwritten() {
        standardRules();
        accounts.takenEmails.add("e1@x.com");
        person("E1", "e1@x.com", tech, positionTl);

        assertThat(logins.candidates(TENANT, null, null, 50).get(0).issues()).containsExactly("EMAIL_IN_USE");
        assertThat(logins.createLogins(TENANT, ACTOR, null, List.of(), null, 50).created()).isZero();
    }

    @Test
    void aFailedAccountCreationIsReportedAndLeavesTheEmployeeUnlinked() {
        standardRules();
        Employee e1 = person("E1", "e1@x.com", tech, positionTl);
        accounts.failCreation = true;

        LoginRun run = logins.createLogins(TENANT, ACTOR, null, List.of(), null, 50);

        assertThat(run.failed()).isEqualTo(1);
        assertThat(run.results().get(0).code()).isEqualTo("CREATE_FAILED");
        assertThat(employees.find(TENANT, e1.id()).orElseThrow().userId()).isNull();
    }

    @Test
    void chosenEmployeesGetLoginsAndTheCallerCannotChooseTheirRoles() {
        standardRules();
        Employee e1 = person("E1", "e1@x.com", tech, positionTl);
        Employee e2 = person("E2", "e2@x.com", testing, positionSe);

        LoginRun run = logins.createLogins(TENANT, ACTOR, null, List.of(e2.id()), null, 50);

        assertThat(run.created()).isEqualTo(1);
        assertThat(accounts.rolesGiven.get(0)).as("roles come from the rules for E2").containsExactly("EMPLOYEE");
        assertThat(employees.find(TENANT, e1.id()).orElseThrow().userId()).isNull();
        assertThat(run.nextAfterEmployeeId()).as("a chosen list has nowhere to continue").isNull();
    }

    @Test
    void rulesAreValidatedWhenCreated() {
        assertThatThrownBy(() -> logins.createRule(TENANT, ACTOR, null, null, "EMPLOYEE"))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("everyone");
        assertThatThrownBy(() -> logins.createRule(TENANT, ACTOR, tech, null, "NO_SUCH_ROLE"))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("Unknown role");
        assertThatThrownBy(() -> logins.createRule(TENANT, ACTOR, tech, null, " "))
                .isInstanceOf(OrgStructureException.class);
        logins.createRule(TENANT, ACTOR, tech, null, "employee");
        assertThatThrownBy(() -> logins.createRule(TENANT, ACTOR, tech, null, "EMPLOYEE"))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> logins.createRule("other-tenant", ACTOR, tech, null, "EMPLOYEE"))
                .isInstanceOf(OrgStructureException.class);
    }

    @Test
    void deletingARuleStopsItGivingRolesToLaterLogins() {
        String ruleId = logins.createRule(TENANT, ACTOR, tech, null, "EMPLOYEE").id();
        person("E1", "e1@x.com", tech, positionSe);
        assertThat(logins.candidates(TENANT, null, null, 50).get(0).ready()).isTrue();

        logins.deleteRule(TENANT, ruleId);

        assertThat(logins.candidates(TENANT, null, null, 50).get(0).issues()).containsExactly("NO_ROLE_RULE");
        assertThatThrownBy(() -> logins.deleteRule(TENANT, ruleId)).isInstanceOf(OrgStructureException.class);
    }

    @Test
    void ruleListShowsDepartmentAndPositionNames() {
        logins.createRule(TENANT, ACTOR, tech, positionTl, "TEAM_LEAD");
        var rule = logins.rules(TENANT).get(0);
        assertThat(rule.unitName()).isEqualTo("Technical");
        assertThat(rule.positionName()).isEqualTo("TL");
        assertThat(logins.rules("other-tenant")).isEmpty();
    }

    private static LoginCandidate find(List<LoginCandidate> list, String code) {
        return list.stream().filter(c -> c.candidate().employeeCode().equals(code)).findFirst().orElseThrow();
    }

    private static Object construct(String simpleName, Object... args) throws Exception {
        var constructor = Class.forName("com.heuristiq.identity.orgstructure.adapter.out." + simpleName).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
}
