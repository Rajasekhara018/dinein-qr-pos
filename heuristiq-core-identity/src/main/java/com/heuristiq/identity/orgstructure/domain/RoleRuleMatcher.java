package com.heuristiq.uam.orgstructure.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.heuristiq.uam.orgstructure.domain.LoginModels.RoleRule;

/** Decides which roles the rules give an employee. Pure logic: it reads nothing and changes nothing. */
public final class RoleRuleMatcher {
    private RoleRuleMatcher() {
    }

    /**
     * The roles from every active rule that matches.
     *
     * @param unitAndAncestors the employee's department and all departments above it
     * @param positionId       the employee's position
     * @return role codes, upper-cased, sorted and without duplicates (a role set has no meaningful order, and sorting keeps
     *         the result the same however the rules happen to be ordered)
     */
    public static List<String> rolesFor(List<RoleRule> rules, Set<String> unitAndAncestors, String positionId) {
        Set<String> roles = new TreeSet<>();
        for (RoleRule rule : rules) {
            if (rule.active() && matches(rule, unitAndAncestors, positionId)) {
                roles.add(rule.roleCode().trim().toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(roles);
    }

    static boolean matches(RoleRule rule, Set<String> unitAndAncestors, String positionId) {
        boolean unitOk = rule.unitId() == null || unitAndAncestors.contains(rule.unitId());
        boolean positionOk = rule.positionId() == null || rule.positionId().equals(positionId);
        // A rule with neither condition would match everyone; it is refused when created, and never matches here.
        boolean constrained = rule.unitId() != null || rule.positionId() != null;
        return constrained && unitOk && positionOk;
    }
}
