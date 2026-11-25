package com.heuristiq.identity.orgstructure.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.heuristiq.identity.orgstructure.domain.EmployeeStore;
import com.heuristiq.identity.orgstructure.domain.LoginAccounts;
import com.heuristiq.identity.orgstructure.domain.LoginModels.Candidate;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginCandidate;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginIssue;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginOutcome;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginResult;
import com.heuristiq.identity.orgstructure.domain.LoginModels.LoginRun;
import com.heuristiq.identity.orgstructure.domain.LoginModels.RoleRule;
import com.heuristiq.identity.orgstructure.domain.Models.Employee;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.RoleRuleMatcher;
import com.heuristiq.identity.orgstructure.domain.RoleRuleStore;

/**
 * Role-mapping rules and login creation. Roles are never typed in by hand per person: they come from the rules, which
 * are applied to where each employee sits. Creating a login always shows (preview) and recomputes (create) the roles
 * on the server, so a caller cannot ask for a role the rules do not give.
 */
@Service
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class OrgLoginService implements OrgLoginUseCase {
    private static final Logger log = LoggerFactory.getLogger(OrgLoginService.class);

    private final RoleRuleStore rules;
    private final EmployeeStore employees;
    private final LoginAccounts accounts;

    public OrgLoginService(RoleRuleStore rules, EmployeeStore employees, LoginAccounts accounts) {
        this.rules = rules;
        this.employees = employees;
        this.accounts = accounts;
    }

    @Override
    public List<RoleRule> rules(String tenantId) {
        return rules.rules(tenantId);
    }

    @Override
    public RoleRule createRule(String tenantId, String actor, String unitId, String positionId, String roleCode) {
        String unit = blankToNull(unitId);
        String position = blankToNull(positionId);
        if (unit == null && position == null) {
            throw OrgStructureException.invalid("A rule needs a department, a position, or both; otherwise it would give the role to everyone");
        }
        if (roleCode == null || roleCode.isBlank()) {
            throw OrgStructureException.invalid("A role is required");
        }
        String role = roleCode.trim().toUpperCase(Locale.ROOT);
        if (!accounts.roleCodes(tenantId).contains(role)) {
            throw OrgStructureException.invalid("Unknown role (or it belongs to another organization): " + role);
        }
        if (rules.exists(tenantId, unit, position, role)) {
            throw OrgStructureException.conflict("That rule already exists");
        }
        return rules.insert(tenantId, actor, unit, position, role);
    }

    @Override
    public void deleteRule(String tenantId, String ruleId) {
        if (!rules.delete(tenantId, ruleId)) {
            throw OrgStructureException.notFound("Rule not found: " + ruleId);
        }
    }

    @Override
    public List<LoginCandidate> candidates(String tenantId, String unitId, String afterEmployeeId, int limit) {
        return evaluate(tenantId, rules.candidates(tenantId, blankToNull(unitId), afterEmployeeId, limit));
    }

    @Override
    public LoginRun createLogins(String tenantId, String actor, String unitId, List<String> employeeIds,
                                 String afterEmployeeId, int limit) {
        boolean chosen = employeeIds != null && !employeeIds.isEmpty();
        List<Candidate> page = chosen
                ? rules.candidatesByIds(tenantId, employeeIds.stream().limit(OrgStructureConstants.MAX_LOGIN_BATCH).toList())
                : rules.candidates(tenantId, blankToNull(unitId), afterEmployeeId, Math.min(limit, OrgStructureConstants.MAX_LOGIN_BATCH));
        List<LoginResult> results = new ArrayList<>(page.size());
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (LoginCandidate candidate : evaluate(tenantId, page)) {
            LoginResult result = candidate.ready() ? createOne(tenantId, candidate) : skipped(candidate);
            results.add(result);
            switch (result.outcome()) {
                case CREATED -> created++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        boolean fullPage = !chosen && page.size() >= Math.max(1, Math.min(limit, OrgStructureConstants.MAX_LOGIN_BATCH));
        String next = fullPage ? page.get(page.size() - 1).employeeId() : null;
        log.info("Logins created by {} for tenant {}: {} created, {} skipped, {} failed", actor, tenantId, created, skipped, failed);
        return new LoginRun(created, skipped, failed, results, next);
    }

    private LoginResult createOne(String tenantId, LoginCandidate candidate) {
        Candidate who = candidate.candidate();
        String userId;
        try {
            userId = accounts.createLogin(tenantId, who.email().trim(), who.fullName(), who.phone(), candidate.roles());
        } catch (OrgStructureException e) {
            log.warn("Login for employee {} was not created: {}", who.employeeCode(), e.getMessage());
            return new LoginResult(who.employeeId(), who.employeeCode(), LoginOutcome.FAILED, null,
                    LoginIssue.CREATE_FAILED.name(), candidate.roles());
        }
        boolean linked = employees.find(tenantId, who.employeeId())
                .map(employee -> employees.update(tenantId, new Employee(employee.id(), employee.employeeCode(), employee.fullName(),
                        employee.email(), employee.phone(), employee.status(), userId, employee.joinedOn())))
                .orElse(false);
        if (!linked) {
            // The account exists but could not be tied to the employee; a later run will see the email as taken.
            log.error("Login {} was created for employee {} but could not be linked", userId, who.employeeCode());
            return new LoginResult(who.employeeId(), who.employeeCode(), LoginOutcome.FAILED, userId,
                    LoginIssue.LINK_FAILED.name(), candidate.roles());
        }
        return new LoginResult(who.employeeId(), who.employeeCode(), LoginOutcome.CREATED, userId, null, candidate.roles());
    }

    private static LoginResult skipped(LoginCandidate candidate) {
        Candidate who = candidate.candidate();
        return new LoginResult(who.employeeId(), who.employeeCode(), LoginOutcome.SKIPPED, null, candidate.issues().get(0),
                candidate.roles());
    }

    /** Works out, for each candidate, the roles the rules give them and what (if anything) blocks a login. */
    private List<LoginCandidate> evaluate(String tenantId, List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<RoleRule> activeRules = rules.rules(tenantId).stream().filter(RoleRule::active).toList();
        Set<String> knownRoles = accounts.roleCodes(tenantId);
        Map<String, Set<String>> ancestorsByUnit = new HashMap<>();
        List<LoginCandidate> out = new ArrayList<>(candidates.size());
        for (Candidate who : candidates) {
            Set<String> ancestors = ancestorsByUnit.computeIfAbsent(who.unitId(), rules::unitAndAncestors);
            List<String> roles = RoleRuleMatcher.rolesFor(activeRules, ancestors, who.positionId());
            List<String> issues = new ArrayList<>();
            if (who.email() == null || who.email().isBlank()) {
                issues.add(LoginIssue.NO_EMAIL.name());
            } else if (accounts.emailInUse(who.email().trim())) {
                issues.add(LoginIssue.EMAIL_IN_USE.name());
            }
            if (roles.isEmpty()) {
                issues.add(LoginIssue.NO_ROLE_RULE.name());
            }
            roles.stream().filter(role -> !knownRoles.contains(role))
                    .forEach(role -> issues.add(LoginIssue.UNKNOWN_ROLE.name() + OrgStructureConstants.LOGIN_ISSUE_DETAIL_SEPARATOR + role));
            out.add(new LoginCandidate(who, roles, List.copyOf(issues)));
        }
        return out;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
