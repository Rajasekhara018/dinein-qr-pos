package com.heuristiq.uam.orgstructure.application;

import java.util.List;
import java.util.Map;

import com.heuristiq.uam.orgstructure.domain.ChangeType;
import com.heuristiq.uam.orgstructure.domain.Models;
import com.heuristiq.uam.orgstructure.domain.Models.Change;
import com.heuristiq.uam.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.uam.orgstructure.domain.Models.DraftResult;
import com.heuristiq.uam.orgstructure.domain.Models.Employee;
import com.heuristiq.uam.orgstructure.domain.Models.LadderView;
import com.heuristiq.uam.orgstructure.domain.Models.PersonView;
import com.heuristiq.uam.orgstructure.domain.Models.PositionView;
import com.heuristiq.uam.orgstructure.domain.Models.UnitView;

/** Use cases of the organization structure: draft editing, preview, publish, and reading the live tree. */
public interface OrgStructureUseCase {

    /** Returns the tenant's open draft, creating one if none exists (one open draft per tenant). */
    ChangeSet openDraft(String tenantId, String actor, String note);

    ChangeSet currentDraft(String tenantId);

    Change addChange(String tenantId, String changeSetId, ChangeType type, Map<String, Object> payload);

    void removeChange(String tenantId, String changeSetId, String changeId);

    List<Change> changes(String tenantId, String changeSetId);

    /** Shows what the organization would look like after the draft, without changing anything. */
    DraftResult preview(String tenantId, String changeSetId);

    /** Makes the draft live; nothing is changed when the result carries ERROR issues. */
    DraftResult publish(String tenantId, String changeSetId, String actor);

    void discard(String tenantId, String changeSetId, String actor);

    /**
     * Undoes the tenant's most recent publish, putting the structure back as it was just before. Refused when something
     * newer exists (another publish, an employee import, or an open draft with changes), because undoing would then also
     * undo or contradict that later work.
     */
    void rollback(String tenantId, String changeSetId, String actor);

    /** Undoes the tenant's most recent publish, whichever draft it was. */
    void rollbackLatest(String tenantId, String actor);

    List<UnitView> children(String tenantId, String parentId);

    /** As {@link #children(String, String)}, showing only what the caller's scope allows. */
    List<UnitView> children(String tenantId, String parentId, com.heuristiq.uam.orgstructure.domain.DataScope scope);

    List<UnitView> searchUnits(String tenantId, String query, int limit);

    List<UnitView> searchUnits(String tenantId, String query, int limit, com.heuristiq.uam.orgstructure.domain.DataScope scope);

    List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit);

    List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit, com.heuristiq.uam.orgstructure.domain.DataScope scope);

    /** A department by its code, with the user accounts of everyone in it and in the departments below it; not found when there is none. */
    Models.DepartmentMembers departmentMembers(String tenantId, String code);

    /** Everyone below this user through reporting lines, at any depth, with who they report to; empty for a non-manager. */
    List<Models.TeamNode> teamTreeOfUser(String tenantId, String userId);

    /** The organization's departments and their heads, for services that act on a head (the wallet funds them). */
    List<Models.DepartmentHead> departmentHeads(String tenantId);

    /** The people who report directly to this user in the organization, as user accounts; empty for a non-manager. */
    List<Models.TeamMember> teamOfUser(String tenantId, String userId);

    /** Codes of the departments (and of those above them) the user belongs to in this organization. */
    List<String> departmentsOfUser(String tenantId, String userId);

    List<LadderView> ladders(String tenantId);

    List<PositionView> positions(String tenantId, String ladderId);

    Employee createEmployee(String tenantId, Employee employee);

    Employee updateEmployee(String tenantId, Employee employee);

    Employee employee(String tenantId, String id);

    List<Employee> listEmployees(String tenantId, String search, String afterId, int limit);

    List<Employee> listEmployees(String tenantId, String search, String afterId, int limit, com.heuristiq.uam.orgstructure.domain.DataScope scope);
}
