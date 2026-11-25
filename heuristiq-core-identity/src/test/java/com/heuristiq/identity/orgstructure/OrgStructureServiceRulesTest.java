package com.heuristiq.identity.orgstructure;

import com.heuristiq.identity.orgstructure.application.OrgStructureChanged;
import com.heuristiq.identity.orgstructure.application.OrgStructureService;
import com.heuristiq.identity.orgstructure.domain.ChangeSetStore;
import com.heuristiq.identity.orgstructure.domain.ChangeType;
import com.heuristiq.identity.orgstructure.domain.DataScope;
import com.heuristiq.identity.orgstructure.domain.EmployeeStore;
import com.heuristiq.identity.orgstructure.domain.ImportStore;
import com.heuristiq.identity.orgstructure.domain.Models.Change;
import com.heuristiq.identity.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.identity.orgstructure.domain.Models.DepartmentHead;
import com.heuristiq.identity.orgstructure.domain.Models.DepartmentMembers;
import com.heuristiq.identity.orgstructure.domain.Models.DraftResult;
import com.heuristiq.identity.orgstructure.domain.Models.Employee;
import com.heuristiq.identity.orgstructure.domain.Models.Severity;
import com.heuristiq.identity.orgstructure.domain.Models.TeamMember;
import com.heuristiq.identity.orgstructure.domain.Models.TeamNode;
import com.heuristiq.identity.orgstructure.domain.Models.UnitView;
import com.heuristiq.identity.orgstructure.domain.Models.ValidationIssue;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;
import com.heuristiq.identity.orgstructure.domain.StructureSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Rules and edge cases of {@link OrgStructureService} that the end-to-end H2 test does not reach, driven with mocked stores. */
class OrgStructureServiceRulesTest {
    private static final String T = "org-a";
    private OrgStructureStore structure;
    private ChangeSetStore changeSets;
    private EmployeeStore employees;
    private StructureSnapshotStore snapshots;
    private ImportStore imports;
    private OrgStructureService service;
    private ApplicationEventPublisher events;

    private final ChangeSet open = new ChangeSet("d1", "OPEN", null, "me", Instant.EPOCH, null, null, 3);
    private final ChangeSet published = new ChangeSet("d1", "PUBLISHED", null, "me", Instant.EPOCH, "me", Instant.parse("2026-01-01T00:00:00Z"), 4);

    @BeforeEach
    void setUp() {
        structure = mock(OrgStructureStore.class);
        changeSets = mock(ChangeSetStore.class);
        employees = mock(EmployeeStore.class);
        snapshots = mock(StructureSnapshotStore.class);
        imports = mock(ImportStore.class);
        events = mock(ApplicationEventPublisher.class);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenAnswer(inv -> mock(TransactionStatus.class));
        service = new OrgStructureService(structure, changeSets, employees, snapshots, imports, tm,
                Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC));
        service.setEvents(events);
        when(changeSets.find(T, "d1")).thenReturn(Optional.of(open));
        when(structure.validate(T)).thenReturn(List.of());
        when(structure.children(T, null)).thenReturn(List.of());
    }

    private Change change(String id, ChangeType type) {
        return new Change(id, "d1", 1, type, Map.of());
    }

    @Test
    void openDraftReusesTheOpenOneOrCreatesANewOne() {
        when(changeSets.findOpen(T)).thenReturn(Optional.of(open));
        assertThat(service.openDraft(T, "me", "n")).isSameAs(open);
        verify(changeSets, never()).create(anyString(), anyString(), anyString());

        when(changeSets.findOpen("org-b")).thenReturn(Optional.empty());
        when(changeSets.create("org-b", "me", "n")).thenReturn(open);
        assertThat(service.openDraft("org-b", "me", "n")).isSameAs(open);
    }

    @Test
    void currentDraftAndChangesFailForUnknownDrafts() {
        when(changeSets.findOpen(T)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.currentDraft(T)).isInstanceOf(OrgStructureException.class).hasMessage("No open draft");

        when(changeSets.find(T, "zz")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changes(T, "zz")).hasMessageContaining("Draft not found");

        Change c = change("c1", ChangeType.UNIT_RENAME);
        when(changeSets.changes("d1")).thenReturn(List.of(c));
        assertThat(service.changes(T, "d1")).containsExactly(c);
    }

    @Test
    void addChangeRequiresTheKeysOfItsType() {
        assertThatThrownBy(() -> service.addChange(T, "d1", ChangeType.UNIT_RENAME, Map.of("id", "u1")))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("requires 'name'");
        assertThatThrownBy(() -> service.addChange(T, "d1", ChangeType.UNIT_RENAME, null))
                .hasMessageContaining("requires 'id'");
        verify(changeSets, never()).addChange(anyString(), any(), any());
    }

    @Test
    void addChangeGeneratesAnIdForCreatingChangesAndKeepsAGivenOne() {
        Change stored = change("c1", ChangeType.UNIT_CREATE);
        when(changeSets.addChange(eq("d1"), eq(ChangeType.UNIT_CREATE), any())).thenReturn(stored);
        when(changeSets.changes("d1")).thenReturn(List.of(stored));

        Change out = service.addChange(T, "d1", ChangeType.UNIT_CREATE, Map.of("code", "X", "name", "Unit X"));

        assertThat(out).isSameAs(stored);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> body = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(changeSets, org.mockito.Mockito.atLeastOnce()).addChange(eq("d1"), eq(ChangeType.UNIT_CREATE), body.capture());
        assertThat(body.getValue().get("id")).asString().isNotBlank();

        service.addChange(T, "d1", ChangeType.UNIT_CREATE, Map.of("id", "mine", "code", "X", "name", "Unit X"));
        verify(changeSets, org.mockito.Mockito.atLeastOnce()).addChange(eq("d1"), eq(ChangeType.UNIT_CREATE), body.capture());
        assertThat(body.getValue().get("id")).isEqualTo("mine");
    }

    @Test
    void aChangeThatBreaksARuleIsRefusedWithThatRule() {
        Change probe = change("c1", ChangeType.UNIT_RENAME);
        when(changeSets.addChange(eq("d1"), eq(ChangeType.UNIT_RENAME), any())).thenReturn(probe);
        when(changeSets.changes("d1")).thenReturn(List.of(probe));
        doThrow(OrgStructureException.invalid("Unit is archived")).when(structure).apply(eq(T), eq(probe), any());

        assertThatThrownBy(() -> service.addChange(T, "d1", ChangeType.UNIT_RENAME, Map.of("id", "u1", "name", "N")))
                .isInstanceOf(OrgStructureException.class).hasMessage("Unit is archived");
    }

    @Test
    void aChangeThatLeavesANewErrorIsRefusedButOldProblemsAreNotBlamedOnIt() {
        Change probe = change("c1", ChangeType.UNIT_RENAME);
        when(changeSets.addChange(eq("d1"), eq(ChangeType.UNIT_RENAME), any())).thenReturn(probe);
        when(changeSets.changes("d1")).thenReturn(List.of(probe));
        ValidationIssue error = new ValidationIssue(Severity.ERROR, "E", "A person is in a department without a ladder", "p1");
        ValidationIssue warning = new ValidationIssue(Severity.WARNING, "W", "Looks odd", "p2");

        // The dry run reports an error, the draft without the change does not: the change is to blame.
        when(structure.validate(T)).thenReturn(List.of(error, warning), List.of());
        assertThatThrownBy(() -> service.addChange(T, "d1", ChangeType.UNIT_RENAME, Map.of("id", "u1", "name", "N")))
                .hasMessage("A person is in a department without a ladder");

        // The same error was already there: the change is accepted.
        when(structure.validate(T)).thenReturn(List.of(error), List.of(error));
        assertThat(service.addChange(T, "d1", ChangeType.UNIT_RENAME, Map.of("id", "u1", "name", "N"))).isSameAs(probe);
    }

    @Test
    void removeChangeRequiresAnOpenDraftAndAnExistingChange() {
        when(changeSets.removeChange("d1", "c1")).thenReturn(true);
        service.removeChange(T, "d1", "c1");
        verify(changeSets).removeChange("d1", "c1");

        when(changeSets.removeChange("d1", "nope")).thenReturn(false);
        assertThatThrownBy(() -> service.removeChange(T, "d1", "nope")).hasMessageContaining("Change not found");

        when(changeSets.find(T, "d2")).thenReturn(Optional.of(published));
        assertThatThrownBy(() -> service.removeChange(T, "d2", "c1")).hasMessageContaining("already PUBLISHED");
    }

    @Test
    void previewReportsAFailingChangeAsAnErrorIssue() {
        Change c = new Change("c9", "d1", 2, ChangeType.UNIT_MOVE, Map.of());
        when(changeSets.changes("d1")).thenReturn(List.of(c));
        doThrow(OrgStructureException.conflict("cycle")).when(structure).apply(eq(T), eq(c), any());

        DraftResult result = service.preview(T, "d1");

        assertThat(result.applied()).isFalse();
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.message()).isEqualTo("Change #2 (UNIT_MOVE): cycle");
            assertThat(i.subjectId()).isEqualTo("c9");
        });
    }

    @Test
    void publishCommitsAnnouncesAndSnapshotsPeopleOnlyWhenTheDraftTouchesThem() {
        when(changeSets.changes("d1")).thenReturn(List.of(change("c1", ChangeType.ASSIGN)));
        when(changeSets.close(T, "d1", 3, "PUBLISHED", "me")).thenReturn(true);

        DraftResult result = service.publish(T, "d1", "me");

        assertThat(result.applied()).isTrue();
        verify(snapshots).capture(T, "d1", true);
        verify(events).publishEvent(any(OrgStructureChanged.class));

        when(changeSets.changes("d1")).thenReturn(List.of(change("c2", ChangeType.UNIT_RENAME)));
        service.publish(T, "d1", "me");
        verify(snapshots).capture(T, "d1", false);
    }

    @Test
    void publishWithAnErrorChangesNothingAndAnnouncesNothing() {
        when(changeSets.changes("d1")).thenReturn(List.of());
        when(structure.validate(T)).thenReturn(List.of(new ValidationIssue(Severity.ERROR, "E", "bad", "x")));

        DraftResult result = service.publish(T, "d1", "me");

        assertThat(result.applied()).isFalse();
        verify(changeSets, never()).close(anyString(), anyString(), anyLong(), anyString(), anyString());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void publishRefusesWhenTheDraftChangedMeanwhileAndWorksWithoutAnEventPublisher() {
        when(changeSets.changes("d1")).thenReturn(List.of());
        when(changeSets.close(T, "d1", 3, "PUBLISHED", "me")).thenReturn(false);
        assertThatThrownBy(() -> service.publish(T, "d1", "me")).hasMessageContaining("changed while publishing");

        when(changeSets.close(T, "d1", 3, "PUBLISHED", "me")).thenReturn(true);
        service.setEvents(null);
        assertThat(service.publish(T, "d1", "me").applied()).isTrue();
    }

    @Test
    void discardClosesTheDraftOrReportsAConflict() {
        when(changeSets.close(T, "d1", 3, "DISCARDED", "me")).thenReturn(true);
        service.discard(T, "d1", "me");

        when(changeSets.close(T, "d1", 3, "DISCARDED", "me")).thenReturn(false);
        assertThatThrownBy(() -> service.discard(T, "d1", "me")).hasMessageContaining("changed meanwhile");
    }

    @Test
    void rollbackLatestNeedsAPublishedChange() {
        when(changeSets.latestPublished(T)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rollbackLatest(T, "me")).hasMessageContaining("no published change");
    }

    @Test
    void rollbackRefusesUnsafeSituations() {
        when(changeSets.find(T, "none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rollback(T, "none", "me")).hasMessageContaining("Draft not found");

        assertThatThrownBy(() -> service.rollback(T, "d1", "me")).hasMessageContaining("Only a published draft");

        when(changeSets.find(T, "d1")).thenReturn(Optional.of(published));
        ChangeSet later = new ChangeSet("d2", "PUBLISHED", null, "me", Instant.EPOCH, "me", Instant.now(), 1);
        when(changeSets.latestPublished(T)).thenReturn(Optional.of(later));
        assertThatThrownBy(() -> service.rollback(T, "d1", "me")).hasMessageContaining("most recent publish");

        when(changeSets.latestPublished(T)).thenReturn(Optional.of(published));
        when(imports.anyAppliedSince(T, published.publishedAt())).thenReturn(true);
        assertThatThrownBy(() -> service.rollback(T, "d1", "me")).hasMessageContaining("employee import");

        when(imports.anyAppliedSince(T, published.publishedAt())).thenReturn(false);
        ChangeSet pending = new ChangeSet("d3", "OPEN", null, "me", Instant.EPOCH, null, null, 1);
        when(changeSets.findOpen(T)).thenReturn(Optional.of(pending));
        when(changeSets.changes("d3")).thenReturn(List.of(change("c", ChangeType.UNIT_RENAME)));
        assertThatThrownBy(() -> service.rollback(T, "d1", "me")).hasMessageContaining("open draft with changes");

        when(changeSets.changes("d3")).thenReturn(List.of());
        when(snapshots.exists("d1")).thenReturn(false);
        assertThatThrownBy(() -> service.rollback(T, "d1", "me")).hasMessageContaining("nothing to restore");

        when(snapshots.exists("d1")).thenReturn(true);
        when(changeSets.markRolledBack(T, "d1", 4)).thenReturn(false);
        assertThatThrownBy(() -> service.rollback(T, "d1", "me")).hasMessageContaining("changed meanwhile");
        verify(snapshots, never()).drop("d1");
    }

    @Test
    void rollbackRestoresDropsTheSnapshotAndAnnounces() {
        when(changeSets.find(T, "d1")).thenReturn(Optional.of(published));
        when(changeSets.latestPublished(T)).thenReturn(Optional.of(published));
        when(changeSets.findOpen(T)).thenReturn(Optional.empty());
        when(snapshots.exists("d1")).thenReturn(true);
        when(changeSets.markRolledBack(T, "d1", 4)).thenReturn(true);

        service.rollbackLatest(T, "me");

        verify(snapshots).restore(T, "d1");
        verify(snapshots).drop("d1");
        verify(events).publishEvent(any(OrgStructureChanged.class));
    }

    @Test
    void readsAreRestrictedOnlyWhenTheScopeIs() {
        DataScope all = DataScope.ALL;
        DataScope limited = new DataScope(true, Set.of("sub"), Set.of("vis"));
        UnitView unit = mock(UnitView.class);
        when(structure.children(T, "p")).thenReturn(List.of(unit));
        when(structure.children(T, "p", Set.of("vis"))).thenReturn(List.of());
        when(structure.searchUnits(T, "q", 5)).thenReturn(List.of(unit));
        when(structure.searchUnits(T, "q", 5, Set.of("vis"))).thenReturn(List.of());
        when(structure.people(T, "u", null, 5)).thenReturn(List.of());
        when(structure.people(T, "u", null, 5, Set.of("sub"))).thenReturn(List.of());
        Employee e = new Employee("e1", "E1", "Jane", null, null, null, null, null);
        when(employees.list(T, "s", null, 5)).thenReturn(List.of(e));
        when(employees.list(T, "s", null, 5, Set.of("sub"))).thenReturn(List.of());

        assertThat(service.children(T, "p", all)).containsExactly(unit);
        assertThat(service.children(T, "p", limited)).isEmpty();
        assertThat(service.children(T, "p")).containsExactly(unit);
        assertThat(service.searchUnits(T, "q", 5, all)).containsExactly(unit);
        assertThat(service.searchUnits(T, "q", 5, limited)).isEmpty();
        assertThat(service.searchUnits(T, "q", 5)).containsExactly(unit);
        assertThat(service.people(T, "u", null, 5, all)).isEmpty();
        assertThat(service.people(T, "u", null, 5, limited)).isEmpty();
        assertThat(service.people(T, "u", null, 5)).isEmpty();
        assertThat(service.listEmployees(T, "s", null, 5, all)).containsExactly(e);
        assertThat(service.listEmployees(T, "s", null, 5, limited)).isEmpty();
        assertThat(service.listEmployees(T, "s", null, 5)).containsExactly(e);
        verify(structure).children(T, "p", Set.of("vis"));
        verify(employees).list(T, "s", null, 5, Set.of("sub"));
    }

    @Test
    void departmentLookupsHandleMissingAndBlankInput() {
        when(structure.activeUnitByCode(T, " ENG ")).thenReturn(Optional.of(new String[] {"u1", "Engineering"}));
        when(structure.userIdsUnder(T, "u1")).thenReturn(List.of("a", "b"));
        when(structure.activeUnitByCode(T, "NOPE")).thenReturn(Optional.empty());
        when(structure.activeUnitByCode(T, null)).thenReturn(Optional.empty());

        assertThat(service.departmentMembers(T, " ENG ")).isEqualTo(new DepartmentMembers(true, "ENG", "Engineering", List.of("a", "b")));
        assertThat(service.departmentMembers(T, "NOPE")).isEqualTo(new DepartmentMembers(false, "NOPE", null, List.of()));
        assertThat(service.departmentMembers(T, null).code()).isNull();
    }

    @Test
    void headsAndTeamsAreEmptyForBlankIdentifiers() {
        DepartmentHead head = new DepartmentHead("u", "C", "N", "h", "H");
        when(structure.departmentHeads(T)).thenReturn(List.of(head));
        TeamNode node = new TeamNode("x", "X", null);
        TeamMember member = new TeamMember("y", "Y");
        when(structure.teamTreeOfUser(T, "m")).thenReturn(List.of(node));
        when(structure.directReportsOfUser(T, "m")).thenReturn(List.of(member));
        when(structure.unitCodesOfUser(T, "m")).thenReturn(List.of("ENG"));

        assertThat(service.departmentHeads(T)).containsExactly(head);
        assertThat(service.departmentHeads(" ")).isEmpty();
        assertThat(service.departmentHeads(null)).isEmpty();
        assertThat(service.teamTreeOfUser(T, "m")).containsExactly(node);
        assertThat(service.teamTreeOfUser(T, " ")).isEmpty();
        assertThat(service.teamTreeOfUser(" ", "m")).isEmpty();
        assertThat(service.teamTreeOfUser(null, "m")).isEmpty();
        assertThat(service.teamOfUser(T, "m")).containsExactly(member);
        assertThat(service.teamOfUser(T, null)).isEmpty();
        assertThat(service.teamOfUser("", "m")).isEmpty();
        assertThat(service.departmentsOfUser(T, "m")).containsExactly("ENG");
        assertThat(service.departmentsOfUser(T, " ")).isEmpty();
    }

    @Test
    void employeesAreCreatedUpdatedAndFoundPerOrganization() {
        Employee e = new Employee("e1", "E1", "Jane", null, null, null, null, null);
        when(employees.insert(T, e)).thenReturn(e);
        when(employees.update(T, e)).thenReturn(true);
        when(employees.find(T, "e1")).thenReturn(Optional.of(e));
        when(employees.find("org-b", "e1")).thenReturn(Optional.empty());

        assertThat(service.createEmployee(T, e)).isSameAs(e);
        assertThat(service.updateEmployee(T, e)).isSameAs(e);
        assertThat(service.employee(T, "e1")).isSameAs(e);
        assertThatThrownBy(() -> service.employee("org-b", "e1")).hasMessageContaining("Employee not found");

        when(employees.update("org-b", e)).thenReturn(false);
        assertThatThrownBy(() -> service.updateEmployee("org-b", e)).hasMessageContaining("Employee not found: e1");
    }

    @Test
    void laddersAndPositionsComeFromTheStore() {
        when(structure.ladders(T)).thenReturn(List.of());
        when(structure.positions(T, "l1")).thenReturn(List.of());
        assertThat(service.ladders(T)).isEmpty();
        assertThat(service.positions(T, "l1")).isEmpty();
    }
}
