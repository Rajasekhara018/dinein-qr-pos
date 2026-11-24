package com.heuristiq.uam.orgstructure.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.heuristiq.uam.orgstructure.domain.Models.Change;
import com.heuristiq.uam.orgstructure.domain.Models.LadderView;
import com.heuristiq.uam.orgstructure.domain.Models.PersonView;
import com.heuristiq.uam.orgstructure.domain.Models.PositionView;
import com.heuristiq.uam.orgstructure.domain.Models.UnitView;
import com.heuristiq.uam.orgstructure.domain.Models.ValidationIssue;

/** Port to the live organization tables (units, ladders, positions, assignments, reporting lines). */
public interface OrgStructureStore {

    /** Applies one draft operation to the live tables; throws {@link OrgStructureException} if it breaks a rule. */
    void apply(String tenantId, Change change, LocalDate today);

    List<ValidationIssue> validate(String tenantId);

    /** Direct children of {@code parentId} (roots when null) with child count and subtree headcount. */
    List<UnitView> children(String tenantId, String parentId);

    /** As {@link #children(String, String)}, limited to the given departments. */
    List<UnitView> children(String tenantId, String parentId, java.util.Set<String> onlyUnits);

    /** Active units whose code or name starts with {@code query} (all when blank), capped, for pickers. */
    List<UnitView> searchUnits(String tenantId, String query, int limit);

    /** As {@link #searchUnits(String, String, int)}, limited to the given departments. */
    List<UnitView> searchUnits(String tenantId, String query, int limit, java.util.Set<String> onlyUnits);

    /** People assigned anywhere under the unit, keyset-paginated by employee id. */
    List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit);

    /** As {@link #people(String, String, String, int)}, counting only people assigned to the given departments. */
    List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit, java.util.Set<String> onlyUnits);

    /** The departments a user is assigned to (their primary assignment). */
    java.util.Set<String> unitIdsOfUser(String tenantId, String userId);

    /** The given departments and every department below them. */
    java.util.Set<String> unitsWithDescendants(java.util.Collection<String> unitIds);

    /** The given departments and every department above them. */
    java.util.Set<String> unitsWithAncestors(java.util.Collection<String> unitIds);

    /** The active department with this code (case-insensitive), as {id, name}, or empty. */
    java.util.Optional<String[]> activeUnitByCode(String tenantId, String code);

    /** User accounts of everyone currently placed in the unit or any unit below it (people without an account are left out). */
    List<String> userIdsUnder(String tenantId, String unitId);

    /**
     * The people who currently report directly to the user's employee record (an open reporting line), as user accounts:
     * active employees without an account are left out. Empty when the user is nobody's manager.
     */
    List<Models.TeamMember> directReportsOfUser(String tenantId, String managerUserId);

    /** Every active department of the organization that has a head with a user account. */
    List<Models.DepartmentHead> departmentHeads(String tenantId);

    /**
     * Everyone below the user through open reporting lines, at any depth (capped), as user accounts with the account of the
     * person they report to. People without an account are left out, so someone below them appears with no manager.
     */
    List<Models.TeamNode> teamTreeOfUser(String tenantId, String managerUserId);

    /** Codes of the active departments the user's employee record currently sits in, and of every department above them. */
    List<String> unitCodesOfUser(String tenantId, String userId);

    /** The employee's current primary placement as {unitId, positionId}, if any. */
    Optional<String[]> currentPrimary(String tenantId, String employeeId);

    /** The employee currently set as head of the department, if any. */
    Optional<String> currentHead(String tenantId, String unitId);

    /** The employee's current primary manager, if any. */
    Optional<String> currentManager(String tenantId, String employeeId);

    List<LadderView> ladders(String tenantId);

    List<PositionView> positions(String tenantId, String ladderId);
}
