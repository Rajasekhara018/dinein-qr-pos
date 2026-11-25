package com.heuristiq.identity.orgstructure.domain;

import java.util.List;
import java.util.Set;

import com.heuristiq.identity.orgstructure.domain.LoginModels.Candidate;
import com.heuristiq.identity.orgstructure.domain.LoginModels.RoleRule;

/** Port to role-mapping rules and to the employees who may be given a login. */
public interface RoleRuleStore {

    RoleRule insert(String tenantId, String actor, String unitId, String positionId, String roleCode);

    /** True when an identical rule (same department, position and role) already exists. */
    boolean exists(String tenantId, String unitId, String positionId, String roleCode);

    boolean delete(String tenantId, String ruleId);

    List<RoleRule> rules(String tenantId);

    /** The department and every department above it. */
    Set<String> unitAndAncestors(String unitId);

    /**
     * Active employees with a primary placement and no login yet, optionally limited to a department and everything
     * under it, keyset-paginated by employee id.
     */
    List<Candidate> candidates(String tenantId, String unitId, String afterEmployeeId, int limit);

    /** The same kind of candidate for specific employees (those that are eligible are returned, the rest omitted). */
    List<Candidate> candidatesByIds(String tenantId, List<String> employeeIds);
}
