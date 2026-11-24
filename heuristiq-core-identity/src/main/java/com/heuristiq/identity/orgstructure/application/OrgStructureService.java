package com.heuristiq.uam.orgstructure.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.heuristiq.uam.orgstructure.domain.ChangeSetStore;
import com.heuristiq.uam.orgstructure.domain.ChangeType;
import com.heuristiq.uam.orgstructure.domain.EmployeeStore;
import com.heuristiq.uam.orgstructure.domain.ImportStore;
import com.heuristiq.uam.orgstructure.domain.StructureSnapshotStore;
import com.heuristiq.uam.orgstructure.domain.DataScope;
import com.heuristiq.uam.orgstructure.domain.Models;
import com.heuristiq.uam.orgstructure.domain.Models.Change;
import com.heuristiq.uam.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.uam.orgstructure.domain.Models.DraftResult;
import com.heuristiq.uam.orgstructure.domain.Models.Employee;
import com.heuristiq.uam.orgstructure.domain.Models.LadderView;
import com.heuristiq.uam.orgstructure.domain.Models.PersonView;
import com.heuristiq.uam.orgstructure.domain.Models.PositionView;
import com.heuristiq.uam.orgstructure.domain.Models.Severity;
import com.heuristiq.uam.orgstructure.domain.Models.UnitView;
import com.heuristiq.uam.orgstructure.domain.Models.ValidationIssue;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.OrgStructureStore;

/**
 * Orchestrates the organization structure. Edits never touch the live tables directly: they are
 * recorded as an ordered draft. Preview applies the draft inside a transaction that is always
 * rolled back; publish applies the same operations and commits only if no ERROR-level issue remains.
 */
@Service
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class OrgStructureService implements OrgStructureUseCase {

    private final OrgStructureStore structure;
    private final ChangeSetStore changeSets;
    private final EmployeeStore employees;
    private final StructureSnapshotStore snapshots;
    private final ImportStore imports;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrgStructureService(OrgStructureStore structure, ChangeSetStore changeSets, EmployeeStore employees,
                               StructureSnapshotStore snapshots, ImportStore imports,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.structure = structure;
        this.changeSets = changeSets;
        this.employees = employees;
        this.snapshots = snapshots;
        this.imports = imports;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public ChangeSet openDraft(String tenantId, String actor, String note) {
        return tx.execute(status -> changeSets.findOpen(tenantId).orElseGet(() -> changeSets.create(tenantId, actor, note)));
    }

    @Override
    public ChangeSet currentDraft(String tenantId) {
        return changeSets.findOpen(tenantId).orElseThrow(() -> OrgStructureException.notFound("No open draft"));
    }

    @Override
    public Change addChange(String tenantId, String changeSetId, ChangeType type, Map<String, Object> payload) {
        Map<String, Object> body = new HashMap<>(payload == null ? Map.of() : payload);
        if (type.generatesId() && isBlank(body.get("id"))) {
            body.put("id", UUID.randomUUID().toString());
        }
        for (String key : type.requiredKeys()) {
            if (isBlank(body.get(key))) {
                throw OrgStructureException.invalid(type + " requires '" + key + "'");
            }
        }
        // Dry run first: the draft with this change added is applied and rolled back, so a change that breaks a rule (a
        // second head for a department, a person already in another department, ...) is refused now, with the reason,
        // instead of being found when the draft is published. Nothing is kept from the dry run.
        String[] refusal = new String[1];
        List<ValidationIssue> withChange = new java.util.ArrayList<>();
        tx.executeWithoutResult(status -> {
            ChangeSet draft = requireOpen(tenantId, changeSetId);
            Change probe = changeSets.addChange(changeSetId, type, body);
            DraftResult result = applyDraft(tenantId, draft);
            if (!result.applied() && !result.issues().isEmpty() && probe.id().equals(result.issues().get(0).subjectId())) {
                refusal[0] = reasonOf(result.issues().get(0).message());
            } else if (result.applied()) {
                withChange.addAll(result.issues());
            }
            status.setRollbackOnly();
        });
        if (refusal[0] != null) {
            throw OrgStructureException.invalid(refusal[0]);
        }
        // The change applied, but it may leave the organization breaking a rule (a person holding a job that is not in their
        // department's ladder, a person placed in a department with no ladder). Such a mistake could not be published and is
        // hard to find later, so it is refused now, with the reason. Problems the draft already had are not blamed on this change.
        if (withChange.stream().anyMatch(i -> i.severity() == Severity.ERROR)) {
            Set<String> existing = new java.util.HashSet<>();
            tx.executeWithoutResult(status -> {
                existing.addAll(errorMessages(applyDraft(tenantId, requireOpen(tenantId, changeSetId)).issues()));
                status.setRollbackOnly();
            });
            withChange.stream().filter(i -> i.severity() == Severity.ERROR).map(ValidationIssue::message)
                    .filter(message -> !existing.contains(message)).findFirst()
                    .ifPresent(message -> {
                        throw OrgStructureException.invalid(message);
                    });
        }
        return tx.execute(status -> {
            requireOpen(tenantId, changeSetId);
            return changeSets.addChange(changeSetId, type, body);
        });
    }

    private static Set<String> errorMessages(List<ValidationIssue> issues) {
        return issues.stream().filter(i -> i.severity() == Severity.ERROR).map(ValidationIssue::message).collect(java.util.stream.Collectors.toSet());
    }

    /** The reason part of a "Change #n (TYPE): reason" issue message. */
    private static String reasonOf(String message) {
        int cut = message.indexOf("): ");
        return cut < 0 ? message : message.substring(cut + 3);
    }

    @Override
    public void removeChange(String tenantId, String changeSetId, String changeId) {
        tx.executeWithoutResult(status -> {
            requireOpen(tenantId, changeSetId);
            if (!changeSets.removeChange(changeSetId, changeId)) {
                throw OrgStructureException.notFound("Change not found: " + changeId);
            }
        });
    }

    @Override
    public List<Change> changes(String tenantId, String changeSetId) {
        requireOpenOrAny(tenantId, changeSetId);
        return changeSets.changes(changeSetId);
    }

    @Override
    public DraftResult preview(String tenantId, String changeSetId) {
        return tx.execute(status -> {
            ChangeSet draft = requireOpen(tenantId, changeSetId);
            DraftResult result = applyDraft(tenantId, draft);
            status.setRollbackOnly();
            return result;
        });
    }

    private org.springframework.context.ApplicationEventPublisher events;

    /** Optional: lets other parts (the search index) hear that the structure changed. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEvents(org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
    }

    private com.heuristiq.uam.orgstructure.domain.DepartmentImportStore departmentImports;

    /**
     * Optional: lets a rollback see that a department import was applied after the publish (undoing the publish would then undo the
     * imported departments too). Not part of the constructor so the service is built exactly as before.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDepartmentImports(com.heuristiq.uam.orgstructure.domain.DepartmentImportStore departmentImports) {
        this.departmentImports = departmentImports;
    }

    private com.heuristiq.uam.orgstructure.domain.LadderImportStore ladderImports;

    /**
     * Optional, like {@link #setDepartmentImports}: lets a rollback see that a ladders import was applied after the publish (undoing
     * the publish would then undo the imported ladders and positions too).
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLadderImports(com.heuristiq.uam.orgstructure.domain.LadderImportStore ladderImports) {
        this.ladderImports = ladderImports;
    }

    private void announce(String tenantId) {
        if (events != null) {
            events.publishEvent(new OrgStructureChanged(tenantId));
        }
    }

    @Override
    public DraftResult publish(String tenantId, String changeSetId, String actor) {
        DraftResult published = publishInTransaction(tenantId, changeSetId, actor);
        if (published.applied()) {
            announce(tenantId);
        }
        return published;
    }

    private DraftResult publishInTransaction(String tenantId, String changeSetId, String actor) {
        return tx.execute(status -> {
            ChangeSet draft = requireOpen(tenantId, changeSetId);
            // Taken in this transaction, before anything is applied: if the publish is refused or fails it is rolled back too.
            snapshots.capture(tenantId, changeSetId, touchesPeople(changeSets.changes(changeSetId)));
            DraftResult result = applyDraft(tenantId, draft);
            boolean hasError = result.issues().stream().anyMatch(i -> i.severity() == Severity.ERROR);
            if (hasError) {
                status.setRollbackOnly();
                return new DraftResult(false, result.issues(), result.rootUnits());
            }
            if (!changeSets.close(tenantId, changeSetId, draft.version(), OrgStructureConstants.DRAFT_PUBLISHED, actor)) {
                throw OrgStructureException.conflict("The draft was changed while publishing; review it and try again");
            }
            return new DraftResult(true, result.issues(), result.rootUnits());
        });
    }

    @Override
    public void discard(String tenantId, String changeSetId, String actor) {
        tx.executeWithoutResult(status -> {
            ChangeSet draft = requireOpen(tenantId, changeSetId);
            if (!changeSets.close(tenantId, changeSetId, draft.version(), OrgStructureConstants.DRAFT_DISCARDED, actor)) {
                throw OrgStructureException.conflict("The draft was changed meanwhile; reload it and try again");
            }
        });
    }

    @Override
    public void rollbackLatest(String tenantId, String actor) {
        ChangeSet latest = changeSets.latestPublished(tenantId)
                .orElseThrow(() -> OrgStructureException.notFound("There is no published change to roll back"));
        rollback(tenantId, latest.id(), actor);
    }

    @Override
    public void rollback(String tenantId, String changeSetId, String actor) {
        rollbackInTransaction(tenantId, changeSetId, actor);
        announce(tenantId);
    }

    private void rollbackInTransaction(String tenantId, String changeSetId, String actor) {
        tx.executeWithoutResult(status -> {
            ChangeSet draft = changeSets.find(tenantId, changeSetId)
                    .orElseThrow(() -> OrgStructureException.notFound("Draft not found: " + changeSetId));
            if (!OrgStructureConstants.DRAFT_PUBLISHED.equals(draft.status())) {
                throw OrgStructureException.conflict("Only a published draft can be rolled back; this one is " + draft.status());
            }
            ChangeSet latest = changeSets.latestPublished(tenantId).orElse(draft);
            if (!latest.id().equals(draft.id())) {
                throw OrgStructureException.conflict("Only the most recent publish can be rolled back; a later one exists");
            }
            if (draft.publishedAt() != null && imports.anyAppliedSince(tenantId, draft.publishedAt())) {
                throw OrgStructureException.conflict("An employee import was applied after this publish; rolling back would undo its placements");
            }
            if (draft.publishedAt() != null && departmentImports != null && departmentImports.anyAppliedSince(tenantId, draft.publishedAt())) {
                throw OrgStructureException.conflict("A department import was applied after this publish; rolling back would undo its departments");
            }
            if (draft.publishedAt() != null && ladderImports != null && ladderImports.anyAppliedSince(tenantId, draft.publishedAt())) {
                throw OrgStructureException.conflict("A ladders import was applied after this publish; rolling back would undo its ladders and positions");
            }
            changeSets.findOpen(tenantId).ifPresent(open -> {
                if (!changeSets.changes(open.id()).isEmpty()) {
                    throw OrgStructureException.conflict("There is an open draft with changes; publish or discard it before rolling back");
                }
            });
            if (!snapshots.exists(changeSetId)) {
                throw OrgStructureException.conflict("This draft was published before rollback existed, so there is nothing to restore");
            }
            snapshots.restore(tenantId, changeSetId);
            if (!changeSets.markRolledBack(tenantId, changeSetId, draft.version())) {
                throw OrgStructureException.conflict("The draft changed meanwhile; reload it and try again");
            }
            snapshots.drop(changeSetId);
        });
    }

    @Override
    public List<UnitView> children(String tenantId, String parentId) {
        return structure.children(tenantId, parentId);
    }

    @Override
    public List<UnitView> children(String tenantId, String parentId, DataScope scope) {
        return scope.restricted() ? structure.children(tenantId, parentId, scope.visible()) : structure.children(tenantId, parentId);
    }

    @Override
    public List<UnitView> searchUnits(String tenantId, String query, int limit, DataScope scope) {
        return scope.restricted() ? structure.searchUnits(tenantId, query, limit, scope.visible())
                : structure.searchUnits(tenantId, query, limit);
    }

    @Override
    public List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit, DataScope scope) {
        // Walking down from a department above the caller shows only the people inside the caller's own departments.
        return scope.restricted() ? structure.people(tenantId, unitId, afterEmployeeId, limit, scope.subtree())
                : structure.people(tenantId, unitId, afterEmployeeId, limit);
    }

    @Override
    public List<Employee> listEmployees(String tenantId, String search, String afterId, int limit, DataScope scope) {
        return scope.restricted() ? employees.list(tenantId, search, afterId, limit, scope.subtree())
                : employees.list(tenantId, search, afterId, limit);
    }

    @Override
    public List<UnitView> searchUnits(String tenantId, String query, int limit) {
        return structure.searchUnits(tenantId, query, limit);
    }

    @Override
    public List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit) {
        return structure.people(tenantId, unitId, afterEmployeeId, limit);
    }

    @Override
    public Models.DepartmentMembers departmentMembers(String tenantId, String code) {
        return structure.activeUnitByCode(tenantId, code)
                .map(unit -> new Models.DepartmentMembers(true, code.trim(), unit[1], structure.userIdsUnder(tenantId, unit[0])))
                .orElseGet(() -> new Models.DepartmentMembers(false, code == null ? null : code.trim(), null, List.of()));
    }

    @Override
    public List<Models.DepartmentHead> departmentHeads(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? List.of() : structure.departmentHeads(tenantId);
    }

    @Override
    public List<Models.TeamNode> teamTreeOfUser(String tenantId, String userId) {
        return userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()
                ? List.of() : structure.teamTreeOfUser(tenantId, userId);
    }

    @Override
    public List<Models.TeamMember> teamOfUser(String tenantId, String userId) {
        return userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()
                ? List.of() : structure.directReportsOfUser(tenantId, userId);
    }

    @Override
    public List<String> departmentsOfUser(String tenantId, String userId) {
        return userId == null || userId.isBlank() ? List.of() : structure.unitCodesOfUser(tenantId, userId);
    }

    @Override
    public List<LadderView> ladders(String tenantId) {
        return structure.ladders(tenantId);
    }

    @Override
    public List<PositionView> positions(String tenantId, String ladderId) {
        return structure.positions(tenantId, ladderId);
    }

    @Override
    public Employee createEmployee(String tenantId, Employee employee) {
        return employees.insert(tenantId, employee);
    }

    @Override
    public Employee updateEmployee(String tenantId, Employee employee) {
        if (!employees.update(tenantId, employee)) {
            throw OrgStructureException.notFound("Employee not found: " + employee.id());
        }
        return employees.find(tenantId, employee.id()).orElseThrow();
    }

    @Override
    public Employee employee(String tenantId, String id) {
        return employees.find(tenantId, id).orElseThrow(() -> OrgStructureException.notFound("Employee not found: " + id));
    }

    @Override
    public List<Employee> listEmployees(String tenantId, String search, String afterId, int limit) {
        return employees.list(tenantId, search, afterId, limit);
    }

    /** Applies every change in order; a failing change is reported as an ERROR issue instead of an exception. */
    private DraftResult applyDraft(String tenantId, ChangeSet draft) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        for (Change change : changeSets.changes(draft.id())) {
            try {
                structure.apply(tenantId, change, today);
            } catch (OrgStructureException e) {
                ValidationIssue issue = new ValidationIssue(Severity.ERROR, "CHANGE_FAILED",
                        "Change #" + change.seq() + " (" + change.type() + "): " + e.getMessage(), change.id());
                // No further queries: a failed statement leaves a PostgreSQL transaction aborted.
                return new DraftResult(false, List.of(issue), List.of());
            }
        }
        return new DraftResult(true, structure.validate(tenantId), structure.children(tenantId, null));
    }

    /** True when the draft touches assignments or reporting lines, which are then part of its snapshot. */
    private static boolean touchesPeople(List<Change> changes) {
        return changes.stream().anyMatch(c -> c.type() == ChangeType.ASSIGN || c.type() == ChangeType.UNASSIGN
                || c.type() == ChangeType.SET_REPORTS_TO);
    }

    private ChangeSet requireOpen(String tenantId, String changeSetId) {
        ChangeSet draft = changeSets.find(tenantId, changeSetId)
                .orElseThrow(() -> OrgStructureException.notFound("Draft not found: " + changeSetId));
        if (!OrgStructureConstants.DRAFT_OPEN.equals(draft.status())) {
            throw OrgStructureException.conflict("Draft is already " + draft.status());
        }
        return draft;
    }

    private void requireOpenOrAny(String tenantId, String changeSetId) {
        changeSets.find(tenantId, changeSetId)
                .orElseThrow(() -> OrgStructureException.notFound("Draft not found: " + changeSetId));
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
