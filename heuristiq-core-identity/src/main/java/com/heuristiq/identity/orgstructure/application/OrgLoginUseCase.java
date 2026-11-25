package com.heuristiq.identity.orgstructure.application;

import java.util.List;

import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginCandidate;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginRun;
import com.heuristiq.identity.orgstructure.domain.LoginModels.RoleRule;

/** Use cases of role-mapping rules and of creating logins for employees from them. */
public interface OrgLoginUseCase {

    List<RoleRule> rules(String tenantId);

    /**
     * Adds a rule giving {@code roleCode} to employees in a department (and below), or in a position, or both.
     * At least one of {@code unitId} and {@code positionId} is required, and the role must exist.
     */
    RoleRule createRule(String tenantId, String actor, String unitId, String positionId, String roleCode);

    void deleteRule(String tenantId, String ruleId);

    /**
     * Preview: employees without a login (optionally in one department and below), with the roles the rules would
     * give them and anything that would stop a login being created. Changes nothing.
     */
    List<LoginCandidate> candidates(String tenantId, String unitId, String afterEmployeeId, int limit);

    /**
     * Creates logins for the chosen employees (or, with {@code employeeIds} empty, for one page of the department's
     * employees starting after {@code afterEmployeeId}). Roles are always recomputed here from the rules, never taken
     * from the caller. Employees that cannot get a login are skipped and reported.
     */
    LoginRun createLogins(String tenantId, String actor, String unitId, List<String> employeeIds, String afterEmployeeId,
                          int limit);
}
