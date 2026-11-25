package com.heuristiq.identity.orgstructure.domain;

import java.util.List;

/** Value types of role-mapping rules and login creation. */
public final class LoginModels {
    private LoginModels() {
    }

    /**
     * A rule giving {@code roleCode} to employees by where they sit. {@code unitId} covers that department and every
     * department below it; {@code positionId} narrows it to one position. At least one of the two is always set.
     */
    public record RoleRule(String id, String unitId, String unitName, String positionId, String positionName,
                           String roleCode, boolean active) {
    }

    /** An employee who has no login yet and sits somewhere in the organization. */
    public record Candidate(String employeeId, String employeeCode, String fullName, String email, String phone,
                            String unitId, String positionId) {
    }

    /** A candidate with the roles the rules give them and anything that stops a login being created. */
    public record LoginCandidate(Candidate candidate, List<String> roles, List<String> issues) {
        public boolean ready() {
            return issues.isEmpty();
        }
    }

    public enum LoginOutcome { CREATED, SKIPPED, FAILED }

    /** What happened for one employee when logins were created; {@code code} is a stable reason for SKIPPED/FAILED. */
    public record LoginResult(String employeeId, String employeeCode, LoginOutcome outcome, String userId, String code,
                              List<String> roles) {
    }

    /**
     * The outcome of one create-logins request. {@code nextAfterEmployeeId} is where to continue (null when there is
     * nothing more to look at), so people who could not be given a login never hold up the ones after them.
     */
    public record LoginRun(int created, int skipped, int failed, List<LoginResult> results, String nextAfterEmployeeId) {
    }

    /** Reasons a login cannot be created; shown to the user as stable codes. */
    public enum LoginIssue {
        NO_EMAIL, NO_ROLE_RULE, UNKNOWN_ROLE, EMAIL_IN_USE, NOT_ELIGIBLE, LINK_FAILED, CREATE_FAILED
    }
}
