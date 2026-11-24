package com.heuristiq.uam.orgstructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.orgstructure.application.DataScopeResolver;
import com.heuristiq.uam.orgstructure.application.OrgStructureService;
import com.heuristiq.uam.orgstructure.domain.ChangeSetStore;
import com.heuristiq.uam.orgstructure.domain.DataScope;
import com.heuristiq.uam.orgstructure.domain.DataScopeSource;
import com.heuristiq.uam.orgstructure.domain.ChangeType;
import com.heuristiq.uam.orgstructure.domain.EmployeeStore;
import com.heuristiq.uam.orgstructure.domain.ImportStore;
import com.heuristiq.uam.orgstructure.domain.StructureSnapshotStore;
import com.heuristiq.uam.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.uam.orgstructure.domain.Models.DraftResult;
import com.heuristiq.uam.orgstructure.domain.Models.Employee;
import com.heuristiq.uam.orgstructure.domain.Models.Severity;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.OrgStructureStore;

/**
 * Drives the organization structure end to end against the real Flyway migrations on H2:
 * draft, preview (must change nothing), publish, move, and the rules that must reject bad input.
 */
class OrgStructureServiceTest {
    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "admin";

    private OrgStructureService service;
    private OrgStructureStore structureStore;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:org_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        // Only V7: earlier migrations contain PostgreSQL-only DO blocks that H2 cannot run, and V7 is self-contained.
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__org_structure.sql"),
                new ClassPathResource("db/migration/V8__org_import.sql"),
                new ClassPathResource("db/migration/V21__org_ladder_import_and_heads.sql"),
                new ClassPathResource("db/migration/V10__org_publish_snapshot.sql")).execute(dataSource);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        // Each reading is a second later, so two publishes never share a timestamp (as in real life, where they are never simultaneous).
        Clock clock = new Clock() {
            private final java.util.concurrent.atomic.AtomicLong seconds = new java.util.concurrent.atomic.AtomicLong();

            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.parse("2026-10-13T10:00:00Z").plusSeconds(seconds.incrementAndGet());
            }
        };

        Class<?> storeClass = Class.forName("com.heuristiq.uam.orgstructure.adapter.out.JdbcOrgStructureStore");
        Class<?> changeClass = Class.forName("com.heuristiq.uam.orgstructure.adapter.out.JdbcChangeSetStore");
        Class<?> employeeClass = Class.forName("com.heuristiq.uam.orgstructure.adapter.out.JdbcEmployeeStore");
        OrgStructureStore structure = (OrgStructureStore) construct(storeClass, jdbc, clock);
        structureStore = structure;
        ChangeSetStore changes = (ChangeSetStore) construct(changeClass, jdbc, new ObjectMapper(), clock);
        EmployeeStore employees = (EmployeeStore) construct(employeeClass, jdbc, clock);
        Class<?> snapshotClass = Class.forName("com.heuristiq.uam.orgstructure.adapter.out.JdbcStructureSnapshotStore");
        Class<?> importClass = Class.forName("com.heuristiq.uam.orgstructure.adapter.out.JdbcImportStore");
        StructureSnapshotStore snapshots = (StructureSnapshotStore) construct(snapshotClass, jdbc, clock);
        ImportStore imports = (ImportStore) construct(importClass, jdbc, clock);
        service = new OrgStructureService(structure, changes, employees, snapshots, imports,
                new DataSourceTransactionManager(dataSource), clock);
    }

    @Test
    void previewChangesNothingAndPublishMakesTheTreeLive() {
        ChangeSet draft = service.openDraft(TENANT, ACTOR, "initial");
        String ladder = add(draft, ChangeType.LADDER_CREATE, Map.of("name", "Tech")).get("id");
        add(draft, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "TL", "rank", 3, "managerial", true));
        String tl = lastPositionId(ladder);
        String root = add(draft, ChangeType.UNIT_CREATE, Map.of("code", "TECH", "name", "Technical", "ladderId", ladder)).get("id");
        add(draft, ChangeType.UNIT_CREATE, Map.of("code", "TEST", "name", "Testing", "parentId", root, "ladderId", ladder));

        DraftResult preview = service.preview(TENANT, draft.id());
        assertThat(preview.applied()).isTrue();
        assertThat(preview.rootUnits()).extracting("code").containsExactly("TECH");
        assertThat(service.children(TENANT, null)).isEmpty();      // preview left the live tables untouched
        assertThat(count("org_unit")).isZero();

        DraftResult published = service.publish(TENANT, draft.id(), ACTOR);
        assertThat(published.applied()).isTrue();
        assertThat(service.children(TENANT, null)).hasSize(1);
        assertThat(service.children(TENANT, root)).extracting("code").containsExactly("TEST");
        assertThat(tl).isNotNull();
    }

    @Test
    void oneOrganizationNeverSeesAnotherOnesChartDraftOrPeople() {
        String other = "tenant-2";
        ChangeSet draft = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(draft, ChangeType.LADDER_CREATE, Map.of("name", "Tech")).get("id");
        unit(draft, "TECH", null, ladder);
        service.createEmployee(TENANT, new Employee(null, "E1", "Asha", "a@x.com", null, null, null, null));
        assertThat(service.publish(TENANT, draft.id(), ACTOR).applied()).isTrue();

        assertThat(service.children(other, null)).isEmpty();
        assertThatThrownBy(() -> service.addChange(other, draft.id(), ChangeType.LADDER_CREATE, Map.of("name", "X")))
                .isInstanceOf(OrgStructureException.class);
        assertThat(service.children(TENANT, null)).hasSize(1);

        // The same department code and employee code may exist in both organizations.
        ChangeSet otherDraft = service.openDraft(other, ACTOR, null);
        assertThat(otherDraft.id()).isNotEqualTo(draft.id());
        String otherLadder = String.valueOf(service.addChange(other, otherDraft.id(), ChangeType.LADDER_CREATE, Map.of("name", "Tech")).payload().get("id"));
        service.addChange(other, otherDraft.id(), ChangeType.UNIT_CREATE, Map.of("code", "TECH", "name", "TECH", "ladderId", otherLadder));
        service.createEmployee(other, new Employee(null, "E1", "Bala", "b@x.com", null, null, null, null));
        assertThat(service.publish(other, otherDraft.id(), ACTOR).applied()).isTrue();
        assertThat(service.children(other, null)).hasSize(1);
        assertThat(service.children(TENANT, null)).hasSize(1);
    }

    @Test
    void aDepartmentByCodeGivesTheAccountsInItAndBelowItAndTheDepartmentsOfAUser() {
        ChangeSet draft = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(draft, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(draft, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        String se = lastPositionId(ladder);
        String sales = unit(draft, "SALES", null, ladder);
        String east = unit(draft, "EAST", sales, ladder);
        unit(draft, "HR", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", "a@x.com", null, null, "user-asha", null));
        Employee bala = service.createEmployee(TENANT, new Employee(null, "E2", "Bala", "b@x.com", null, null, "user-bala", null));
        Employee noAccount = service.createEmployee(TENANT, new Employee(null, "E3", "Chitra", "c@x.com", null, null, null, null));
        add(draft, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", sales, "positionId", se));
        add(draft, ChangeType.ASSIGN, Map.of("employeeId", bala.id(), "unitId", east, "positionId", se));
        add(draft, ChangeType.ASSIGN, Map.of("employeeId", noAccount.id(), "unitId", east, "positionId", se));
        assertThat(service.publish(TENANT, draft.id(), ACTOR).applied()).isTrue();

        var inSales = service.departmentMembers(TENANT, "sales");
        assertThat(inSales.found()).isTrue();
        assertThat(inSales.userIds()).containsExactlyInAnyOrder("user-asha", "user-bala");
        assertThat(service.departmentMembers(TENANT, "EAST").userIds()).containsExactly("user-bala");
        assertThat(service.departmentMembers(TENANT, "HR").userIds()).isEmpty();
        assertThat(service.departmentMembers(TENANT, "NOPE").found()).isFalse();
        assertThat(service.departmentMembers("tenant-2", "SALES").found()).isFalse();

        assertThat(service.departmentsOfUser(TENANT, "user-bala")).containsExactlyInAnyOrder("EAST", "SALES");
        assertThat(service.departmentsOfUser(TENANT, "user-asha")).containsExactly("SALES");
        assertThat(service.departmentsOfUser(TENANT, "nobody")).isEmpty();
        assertThat(service.departmentsOfUser("tenant-2", "user-bala")).isEmpty();
    }

    @Test
    void peopleUnderAUnitIncludeSubUnitsAndSurviveAMove() {
        ChangeSet d1 = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(d1, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(d1, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        String se = lastPositionId(ladder);
        String tech = unit(d1, "TECH", null, ladder);
        String hr = unit(d1, "HR", null, ladder);
        String test = unit(d1, "TEST", tech, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", "a@x.com", null, null, null, null));
        add(d1, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", test, "positionId", se));
        assertThat(service.publish(TENANT, d1.id(), ACTOR).applied()).isTrue();

        assertThat(service.people(TENANT, tech, null, 50)).extracting("employeeCode").containsExactly("E1");
        assertThat(service.people(TENANT, hr, null, 50)).isEmpty();

        ChangeSet d2 = service.openDraft(TENANT, ACTOR, null);
        add(d2, ChangeType.UNIT_MOVE, Map.of("id", test, "newParentId", hr));
        assertThat(service.publish(TENANT, d2.id(), ACTOR).applied()).isTrue();

        assertThat(service.people(TENANT, hr, null, 50)).extracting("employeeCode").containsExactly("E1");
        assertThat(service.people(TENANT, tech, null, 50)).isEmpty();
        assertThat(service.children(TENANT, null)).extracting("headcount").containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void movingAUnitUnderItsOwnSubUnitIsRejected() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String a = unit(d, "A", null, null);
        String b = unit(d, "B", a, null);

        // refused when it is added, with the reason, and not kept in the draft
        assertThatThrownBy(() -> add(d, ChangeType.UNIT_MOVE, Map.of("id", a, "newParentId", b)))
                .isInstanceOf(OrgStructureException.class);
        assertThat(service.changes(TENANT, d.id())).hasSize(2);
        assertThat(count("org_unit")).isZero();                     // the dry run left nothing behind
    }

    @Test
    void positionFromAnotherLadderCannotBeAssigned() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String tech = add(d, ChangeType.LADDER_CREATE, Map.of("name", "Tech")).get("id");
        String fin = add(d, ChangeType.LADDER_CREATE, Map.of("name", "Finance")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", fin, "name", "Accountant", "rank", 5));
        String accountant = lastPositionId(fin);
        String unit = unit(d, "TECH", null, tech);
        Employee e = service.createEmployee(TENANT, new Employee(null, "E2", "Ravi", null, null, null, null, null));

        assertThatThrownBy(() -> add(d, ChangeType.ASSIGN, Map.of("employeeId", e.id(), "unitId", unit, "positionId", accountant)))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("ladder");
    }

    @Test
    void changingTheLadderOfADepartmentWhosePeopleHoldJobsOfTheOldOneIsRefusedNow() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String tech = add(d, ChangeType.LADDER_CREATE, Map.of("name", "Tech")).get("id");
        String fin = add(d, ChangeType.LADDER_CREATE, Map.of("name", "Finance")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", tech, "name", "Engineer", "rank", 5));
        String engineer = lastPositionId(tech);
        String unit = unit(d, "TECH", null, tech);
        Employee e = service.createEmployee(TENANT, new Employee(null, "E9", "Meera", null, null, null, null, null));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", e.id(), "unitId", unit, "positionId", engineer));

        // moving the department to a ladder that does not have Meera's job would only be found at publish: it is refused when added
        assertThatThrownBy(() -> add(d, ChangeType.UNIT_SET_LADDER, Map.of("unitId", unit, "ladderId", fin)))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("Meera").hasMessageContaining("ladder");
        // and the draft is left as it was: the refused change was not kept
        assertThat(service.changes(TENANT, d.id())).noneMatch(c -> c.type() == ChangeType.UNIT_SET_LADDER);
    }

    @Test
    void unitSearchMatchesCodeOrNamePrefixAndIsCapped() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        unit(d, "TECH", null, null);
        unit(d, "TEST", null, null);
        unit(d, "HR", null, null);
        service.publish(TENANT, d.id(), ACTOR);

        assertThat(service.searchUnits(TENANT, "te", 10)).extracting("code").containsExactlyInAnyOrder("TECH", "TEST");
        assertThat(service.searchUnits(TENANT, "hr", 10)).extracting("code").containsExactly("HR");
        assertThat(service.searchUnits(TENANT, "zzz", 10)).isEmpty();
        assertThat(service.searchUnits(TENANT, "", 2)).hasSize(2);
        assertThat(service.searchUnits("other-tenant", "te", 10)).isEmpty();
    }

    @Test
    void copiedLadderIsIndependentOfItsSource() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String source = add(d, ChangeType.LADDER_CREATE, Map.of("name", "Tech")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", source, "name", "TL", "rank", 3));
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", source, "name", "SE", "rank", 6));
        String copy = add(d, ChangeType.LADDER_COPY, Map.of("sourceLadderId", source, "name", "Testing")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", copy, "name", "QA Lead", "rank", 4));
        service.publish(TENANT, d.id(), ACTOR);

        assertThat(service.positions(TENANT, source)).extracting("name").containsExactly("TL", "SE");
        assertThat(service.positions(TENANT, copy)).extracting("name").containsExactly("TL", "QA Lead", "SE");
    }

    @Test
    void reportingLineCannotFormACycle() {
        Employee boss = service.createEmployee(TENANT, new Employee(null, "B", "Boss", null, null, null, null, null));
        Employee worker = service.createEmployee(TENANT, new Employee(null, "W", "Worker", null, null, null, null, null));
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        add(d, ChangeType.SET_REPORTS_TO, Map.of("employeeId", worker.id(), "managerEmployeeId", boss.id()));

        assertThatThrownBy(() -> add(d, ChangeType.SET_REPORTS_TO, Map.of("employeeId", boss.id(), "managerEmployeeId", worker.id())))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("cycle");
    }

    @Test
    void aManagersTeamIsTheirDirectReportsWithAnAccountAndIsPerOrganization() {
        Employee boss = service.createEmployee(TENANT, new Employee(null, "B", "Boss", null, null, null, "user-boss", null));
        Employee asha = service.createEmployee(TENANT, new Employee(null, "A", "Asha", null, null, null, "user-asha", null));
        Employee bala = service.createEmployee(TENANT, new Employee(null, "C", "Bala", null, null, null, "user-bala", null));
        Employee noAccount = service.createEmployee(TENANT, new Employee(null, "D", "Chitra", null, null, null, null, null));
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        add(d, ChangeType.SET_REPORTS_TO, Map.of("employeeId", asha.id(), "managerEmployeeId", boss.id()));
        add(d, ChangeType.SET_REPORTS_TO, Map.of("employeeId", noAccount.id(), "managerEmployeeId", boss.id()));
        add(d, ChangeType.SET_REPORTS_TO, Map.of("employeeId", bala.id(), "managerEmployeeId", asha.id()));
        assertThat(service.publish(TENANT, d.id(), ACTOR).applied()).isTrue();

        assertThat(service.teamOfUser(TENANT, "user-boss")).extracting("userId", "fullName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("user-asha", "Asha"));
        // a cascade: Asha manages Bala, but Bala is not the boss's direct report
        assertThat(service.teamOfUser(TENANT, "user-asha")).extracting("userId").containsExactly("user-bala");
        assertThat(service.teamOfUser(TENANT, "user-bala")).isEmpty();
        assertThat(service.teamOfUser(TENANT, "nobody")).isEmpty();
        assertThat(service.teamOfUser("tenant-2", "user-boss")).isEmpty();
        assertThat(service.teamOfUser(null, "user-boss")).isEmpty();
    }

    @Test
    void theTeamTreeIncludesEveryoneBelowAtAnyDepthWithWhoTheyReportTo() {
        Employee boss = service.createEmployee(TENANT, new Employee(null, "B", "Boss", null, null, null, "user-boss", null));
        Employee asha = service.createEmployee(TENANT, new Employee(null, "A", "Asha", null, null, null, "user-asha", null));
        Employee bala = service.createEmployee(TENANT, new Employee(null, "C", "Bala", null, null, null, "user-bala", null));
        Employee elsewhere = service.createEmployee(TENANT, new Employee(null, "E", "Elsewhere", null, null, null, "user-else", null));
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        add(d, ChangeType.SET_REPORTS_TO, Map.of("employeeId", asha.id(), "managerEmployeeId", boss.id()));
        add(d, ChangeType.SET_REPORTS_TO, Map.of("employeeId", bala.id(), "managerEmployeeId", asha.id()));
        assertThat(service.publish(TENANT, d.id(), ACTOR).applied()).isTrue();

        assertThat(service.teamTreeOfUser(TENANT, "user-boss")).extracting("userId", "managerUserId")
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("user-asha", "user-boss"),
                        org.assertj.core.groups.Tuple.tuple("user-bala", "user-asha"));
        assertThat(service.teamTreeOfUser(TENANT, "user-asha")).extracting("userId").containsExactly("user-bala");
        assertThat(service.teamTreeOfUser(TENANT, "user-bala")).isEmpty();
        assertThat(service.teamTreeOfUser("tenant-2", "user-boss")).isEmpty();
        assertThat(elsewhere.id()).isNotNull();
    }

    @Test
    void malformedChangeIsRejectedWhenAdded() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        assertThatThrownBy(() -> service.addChange(TENANT, d.id(), ChangeType.UNIT_CREATE, Map.of("name", "No code")))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("code");
    }

    @Test
    void anotherTenantCannotSeeOrEditTheDraft() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        assertThatThrownBy(() -> service.preview("other-tenant", d.id())).isInstanceOf(OrgStructureException.class);
        assertThat(service.openDraft("other-tenant", ACTOR, null).id()).isNotEqualTo(d.id());
    }

    // ---- rollback of the last publish ----------------------------------------------------------

    @Test
    void rollingBackAPublishRestoresTheStructureAsItWasBefore() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(first, ChangeType.LADDER_CREATE, Map.of("name", "Tech")).get("id");
        String tech = unit(first, "TECH", null, ladder);
        String test = unit(first, "TEST", tech, ladder);
        assertThat(service.publish(TENANT, first.id(), ACTOR).applied()).isTrue();

        ChangeSet second = service.openDraft(TENANT, ACTOR, null);
        add(second, ChangeType.UNIT_RENAME, Map.of("id", test, "name", "Quality"));
        String hr = unit(second, "HR", null, ladder);
        add(second, ChangeType.UNIT_MOVE, Map.of("id", test, "newParentId", hr));
        assertThat(service.publish(TENANT, second.id(), ACTOR).applied()).isTrue();
        assertThat(service.children(TENANT, hr)).extracting("name").containsExactly("Quality");

        service.rollback(TENANT, second.id(), ACTOR);

        assertThat(service.children(TENANT, null)).extracting("code").containsExactly("TECH");   // HR is gone again
        assertThat(service.children(TENANT, tech)).extracting("code", "name").containsExactly(org.assertj.core.groups.Tuple.tuple("TEST", "TEST"));
        assertThat(count("org_unit")).isEqualTo(2);
        assertThat(count("org_unit_closure")).isEqualTo(3);       // (TECH,TECH) (TEST,TEST) (TECH,TEST)
        assertThat(count("org_snap_unit")).isZero();              // the snapshot is dropped
        assertThat(jdbc.queryForObject("select status from org_change_set where id = :id", Map.of("id", second.id()), String.class))
                .isEqualTo("ROLLED_BACK");
    }

    @Test
    void rollingBackPutsPeopleBackToo() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(first, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(first, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        String se = lastPositionId(ladder);
        String tech = unit(first, "TECH", null, ladder);
        String hr = unit(first, "HR", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, null, null));
        add(first, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", se));
        assertThat(service.publish(TENANT, first.id(), ACTOR).applied()).isTrue();

        // A person belongs to one department, so moving them leaves the first one before joining the second.
        ChangeSet second = service.openDraft(TENANT, ACTOR, null);
        add(second, ChangeType.UNASSIGN, Map.of("employeeId", asha.id(), "unitId", tech));
        add(second, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", hr, "positionId", se));
        assertThat(service.publish(TENANT, second.id(), ACTOR).applied()).isTrue();
        assertThat(service.people(TENANT, hr, null, 50)).hasSize(1);

        service.rollback(TENANT, second.id(), ACTOR);

        assertThat(service.people(TENANT, tech, null, 50)).extracting("employeeCode").containsExactly("E1");
        assertThat(service.people(TENANT, hr, null, 50)).isEmpty();
    }

    @Test
    void aPersonCannotBelongToTwoDepartments() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(first, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(first, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        String se = lastPositionId(ladder);
        String tech = unit(first, "TECH", null, ladder);
        String hr = unit(first, "HR", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, null, null));
        add(first, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", se));

        // the second department is refused straight away, naming the first one
        assertThatThrownBy(() -> add(first, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", hr, "positionId", se)))
                .isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("one department only").hasMessageContaining("TECH");

        assertThat(service.publish(TENANT, first.id(), ACTOR).applied()).isTrue();
        assertThat(service.people(TENANT, tech, null, 50)).hasSize(1);
        assertThat(service.people(TENANT, hr, null, 50)).isEmpty();
    }

    @Test
    void aPersonCanMoveToAnotherDepartmentByLeavingTheFirstInTheSameDraft() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(first, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(first, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        String se = lastPositionId(ladder);
        String tech = unit(first, "TECH", null, ladder);
        String hr = unit(first, "HR", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, null, null));
        add(first, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", se));
        add(first, ChangeType.UNASSIGN, Map.of("employeeId", asha.id(), "unitId", tech));
        add(first, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", hr, "positionId", se));

        assertThat(service.publish(TENANT, first.id(), ACTOR).applied()).isTrue();
        assertThat(service.people(TENANT, tech, null, 50)).isEmpty();
        assertThat(service.people(TENANT, hr, null, 50)).hasSize(1);
    }

    @Test
    void aDepartmentHasOneHeadAndASecondPersonIsRefusedUntilTheFirstIsRemoved() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(d, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "Mgr", "rank", 3));
        String position = lastPositionId(ladder);
        String tech = unit(d, "TECH", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, null, null));
        Employee bala = service.createEmployee(TENANT, new Employee(null, "E2", "Bala", null, null, null, "user-bala", null));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", position));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", bala.id(), "unitId", tech, "positionId", position));
        add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", asha.id()));

        // a second head is refused, naming the current one, and is not kept in the draft
        assertThatThrownBy(() -> add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", bala.id())))
                .isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("Asha").hasMessageContaining("already the head").hasMessageContaining("TECH");
        int changes = service.changes(TENANT, d.id()).size();

        // removing the head first, then choosing the new one, is allowed
        add(d, ChangeType.UNIT_CLEAR_HEAD, Map.of("unitId", tech));
        add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", bala.id()));
        assertThat(service.changes(TENANT, d.id())).hasSize(changes + 2);

        assertThat(service.publish(TENANT, d.id(), ACTOR).applied()).isTrue();
        var live = service.children(TENANT, null).get(0);
        assertThat(live.headEmployeeId()).isEqualTo(bala.id());
        assertThat(live.headName()).isEqualTo("Bala");
        assertThat(live.headUserId()).as("so screens can open the head's wallet").isEqualTo("user-bala");
        assertThat(service.people(TENANT, live.id(), null, 50)).extracting("fullName", "userId")
                .contains(org.assertj.core.groups.Tuple.tuple("Bala", "user-bala"));
    }

    @Test
    void choosingTheSameHeadAgainIsHarmlessAndAHeadMustBeInTheDepartment() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(d, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "Mgr", "rank", 3));
        String position = lastPositionId(ladder);
        String tech = unit(d, "TECH", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, null, null));
        Employee outsider = service.createEmployee(TENANT, new Employee(null, "E9", "Outsider", null, null, null, null, null));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", position));
        add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", asha.id()));
        add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", asha.id()));

        assertThatThrownBy(() -> add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", outsider.id())))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("assigned to that unit first");
    }

    @Test
    void whoLeavesADepartmentIsNoLongerItsHead() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(d, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "Mgr", "rank", 3));
        String position = lastPositionId(ladder);
        String tech = unit(d, "TECH", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, null, null));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", position));
        add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", asha.id()));
        assertThat(service.publish(TENANT, d.id(), ACTOR).applied()).isTrue();

        ChangeSet second = service.openDraft(TENANT, ACTOR, null);
        add(second, ChangeType.UNASSIGN, Map.of("employeeId", asha.id(), "unitId", tech));
        assertThat(service.publish(TENANT, second.id(), ACTOR).applied()).isTrue();

        assertThat(service.children(TENANT, null).get(0).headEmployeeId()).isNull();
    }

    @Test
    void assigningAgainInTheSameDepartmentChangesTheJobTitle() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(first, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(first, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "SE", "rank", 6));
        String se = lastPositionId(ladder);
        add(first, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "Lead", "rank", 7));
        String lead = lastPositionId(ladder);
        String tech = unit(first, "TECH", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, null, null));
        add(first, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", se));
        add(first, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", lead));

        assertThat(service.publish(TENANT, first.id(), ACTOR).applied()).isTrue();

        assertThat(service.people(TENANT, tech, null, 50)).extracting("positionName").containsExactly("Lead");
    }

    @Test
    void onlyTheMostRecentPublishCanBeRolledBack() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        unit(first, "A", null, null);
        service.publish(TENANT, first.id(), ACTOR);
        ChangeSet second = service.openDraft(TENANT, ACTOR, null);
        unit(second, "B", null, null);
        service.publish(TENANT, second.id(), ACTOR);

        assertThatThrownBy(() -> service.rollback(TENANT, first.id(), ACTOR))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("most recent");
        assertThat(count("org_unit")).isEqualTo(2);
    }

    @Test
    void anOpenDraftWithChangesOrAnUnpublishedDraftBlocksRollback() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        unit(first, "A", null, null);
        service.publish(TENANT, first.id(), ACTOR);
        ChangeSet open = service.openDraft(TENANT, ACTOR, null);
        unit(open, "B", null, null);

        assertThatThrownBy(() -> service.rollback(TENANT, first.id(), ACTOR)).hasMessageContaining("open draft");
        assertThatThrownBy(() -> service.rollback(TENANT, open.id(), ACTOR)).hasMessageContaining("published");
        assertThatThrownBy(() -> service.rollback("other-tenant", first.id(), ACTOR)).isInstanceOf(OrgStructureException.class);
    }

    @Test
    void anImportAppliedAfterThePublishBlocksRollback() {
        ChangeSet first = service.openDraft(TENANT, ACTOR, null);
        unit(first, "A", null, null);
        service.publish(TENANT, first.id(), ACTOR);
        jdbc.update("insert into org_import (id, tenant_id, status, created_at, applied_at, version) values ('i1', :t, 'APPLIED', :at, :at, 0)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource().addValue("t", TENANT)
                        .addValue("at", java.sql.Timestamp.from(Instant.parse("2026-10-13T11:00:00Z"))));

        assertThatThrownBy(() -> service.rollback(TENANT, first.id(), ACTOR)).hasMessageContaining("import");
    }

    @Test
    void aPublishThatFailsLeavesNoSnapshotBehind() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String a = unit(d, "A", null, null);
        assertThat(service.publish(TENANT, d.id(), ACTOR).applied()).isTrue();
        int snapshots = count("org_change_set_snapshot");

        // valid when added; the unit is archived behind the draft's back (as if another change landed first), so the publish fails
        ChangeSet second = service.openDraft(TENANT, ACTOR, null);
        add(second, ChangeType.UNIT_MOVE, Map.of("id", a));
        jdbc.update("update org_unit set status = 'ARCHIVED' where id = :id", Map.of("id", a));

        assertThat(service.publish(TENANT, second.id(), ACTOR).applied()).isFalse();

        assertThat(count("org_change_set_snapshot")).isEqualTo(snapshots);
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** TECH (with BACKEND below it) and SALES; a lead in BACKEND, a manager in TECH, a rep in SALES. Returns nothing; all live. */
    private void publishDepartmentsWithPeople() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(d, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "Staff", "rank", 1));
        String position = lastPositionId(ladder);
        String tech = unit(d, "TECH", null, ladder);
        String backend = unit(d, "BACKEND", tech, ladder);
        String sales = unit(d, "SALES", null, ladder);
        Employee lead = service.createEmployee(TENANT, new Employee(null, "E1", "Lead", null, null, null, "user-lead", null));
        Employee manager = service.createEmployee(TENANT, new Employee(null, "E2", "Manager", null, null, null, "user-mgr", null));
        Employee rep = service.createEmployee(TENANT, new Employee(null, "E3", "Rep", null, null, null, "user-rep", null));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", lead.id(), "unitId", backend, "positionId", position));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", manager.id(), "unitId", tech, "positionId", position));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", rep.id(), "unitId", sales, "positionId", position));
        assertThat(service.publish(TENANT, d.id(), ACTOR).applied()).isTrue();
    }

    private DataScopeResolver resolver(boolean departmentScoped) {
        return new DataScopeResolver(new DataScopeSource() {
            @Override
            public boolean isDepartmentScoped(String tenantId) {
                return departmentScoped;
            }

            @Override
            public String adminRoleCode(String tenantId) {
                return "ACME_ADMIN";
            }
        }, structureStore);
    }

    @Test
    void aPersonInADepartmentScopedOrganizationSeesOnlyTheirDepartmentAndBelow() {
        publishDepartmentsWithPeople();
        DataScope scope = resolver(true).resolve(TENANT, "user-mgr", java.util.List.of("OPS"));

        // the top level shows TECH (the manager's own department) but not SALES
        assertThat(service.children(TENANT, null, scope)).extracting(u -> u.code()).containsExactly("TECH");
        // BACKEND is below the manager, so it opens
        String tech = service.children(TENANT, null).stream().filter(u -> u.code().equals("TECH")).findFirst().orElseThrow().id();
        assertThat(service.children(TENANT, tech, scope)).extracting(u -> u.code()).containsExactly("BACKEND");
        // people of TECH: the manager and the lead (below), never the rep
        assertThat(service.people(TENANT, tech, null, 50, scope)).extracting(p -> p.fullName()).containsExactlyInAnyOrder("Lead", "Manager");
        assertThat(service.listEmployees(TENANT, null, null, 50, scope)).extracting(e -> e.fullName()).containsExactlyInAnyOrder("Lead", "Manager");
        assertThat(service.searchUnits(TENANT, null, 50, scope)).extracting(u -> u.code()).containsExactlyInAnyOrder("TECH", "BACKEND");
    }

    @Test
    void theHeadsOfDepartmentsAreListedForFunding() {
        ChangeSet d = service.openDraft(TENANT, ACTOR, null);
        String ladder = add(d, ChangeType.LADDER_CREATE, Map.of("name", "L")).get("id");
        add(d, ChangeType.POSITION_UPSERT, Map.of("ladderId", ladder, "name", "Mgr", "rank", 1));
        String position = lastPositionId(ladder);
        String tech = unit(d, "TECH", null, ladder);
        unit(d, "SALES", null, ladder);
        Employee asha = service.createEmployee(TENANT, new Employee(null, "E1", "Asha", null, null, null, "user-asha", null));
        Employee noAccount = service.createEmployee(TENANT, new Employee(null, "E2", "Bala", null, null, null, null, null));
        add(d, ChangeType.ASSIGN, Map.of("employeeId", asha.id(), "unitId", tech, "positionId", position));
        add(d, ChangeType.UNIT_SET_HEAD, Map.of("unitId", tech, "employeeId", asha.id()));
        assertThat(service.publish(TENANT, d.id(), ACTOR).applied()).isTrue();

        // only a department whose head has a user account is listed; SALES has no head
        assertThat(structureStore.departmentHeads(TENANT)).extracting(h -> h.unitCode() + ":" + h.headUserId()).containsExactly("TECH:user-asha");
        assertThat(structureStore.departmentHeads("another-tenant")).isEmpty();
    }

    @Test
    void aLeadSeesNothingAboveTheirOwnDepartmentExceptThePathDownToIt() {
        publishDepartmentsWithPeople();
        DataScope scope = resolver(true).resolve(TENANT, "user-lead", java.util.List.of("OPS"));

        String tech = service.children(TENANT, null).stream().filter(u -> u.code().equals("TECH")).findFirst().orElseThrow().id();
        // TECH shows (the way down), but only the lead is visible in it
        assertThat(service.children(TENANT, null, scope)).extracting(u -> u.code()).containsExactly("TECH");
        assertThat(service.people(TENANT, tech, null, 50, scope)).extracting(p -> p.fullName()).containsExactly("Lead");
    }

    @Test
    void administratorsAndUnscopedOrganizationsSeeEverything() {
        publishDepartmentsWithPeople();

        assertThat(resolver(true).resolve(TENANT, "user-rep", java.util.List.of("ACME_ADMIN")).restricted()).isFalse();
        assertThat(resolver(true).resolve(TENANT, "user-rep", java.util.List.of("SUPER_ADMIN")).restricted()).isFalse();
        assertThat(resolver(false).resolve(TENANT, "user-rep", java.util.List.of("OPS")).restricted()).isFalse();
        assertThat(service.listEmployees(TENANT, null, null, 50, DataScope.ALL)).hasSize(3);
    }

    @Test
    void aPersonWithNoDepartmentSeesNoneOfTheStructure() {
        publishDepartmentsWithPeople();
        DataScope scope = resolver(true).resolve(TENANT, "user-nobody", java.util.List.of("OPS"));

        assertThat(service.children(TENANT, null, scope)).isEmpty();
        assertThat(service.listEmployees(TENANT, null, null, 50, scope)).isEmpty();
    }

    private Map<String, String> add(ChangeSet draft, ChangeType type, Map<String, Object> payload) {
        Map<String, Object> stored = service.addChange(TENANT, draft.id(), type, payload).payload();
        Map<String, String> out = new java.util.HashMap<>();
        stored.forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    private String unit(ChangeSet draft, String code, String parentId, String ladderId) {
        Map<String, Object> payload = new java.util.HashMap<>(Map.of("code", code, "name", code));
        if (parentId != null) {
            payload.put("parentId", parentId);
        }
        if (ladderId != null) {
            payload.put("ladderId", ladderId);
        }
        return add(draft, ChangeType.UNIT_CREATE, payload).get("id");
    }

    /** The id of the position just added to the draft for the ladder (read back from the stored change). */
    private String lastPositionId(String ladderId) {
        ChangeSet draft = service.currentDraft(TENANT);
        List<com.heuristiq.uam.orgstructure.domain.Models.Change> all = service.changes(TENANT, draft.id());
        for (int i = all.size() - 1; i >= 0; i--) {
            var change = all.get(i);
            if (change.type() == ChangeType.POSITION_UPSERT && ladderId.equals(String.valueOf(change.payload().get("ladderId")))) {
                return String.valueOf(change.payload().get("id"));
            }
        }
        return null;
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("select count(*) from " + table, Map.of(), Integer.class);
        return n == null ? 0 : n;
    }

    private static Object construct(Class<?> type, Object... args) throws Exception {
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
}
