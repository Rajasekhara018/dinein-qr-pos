package com.heuristiq.uam.orgstructure.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.orgstructure.domain.LoginModels.RoleRule;

class RoleRuleMatcherTest {
    private static RoleRule rule(String unit, String position, String role, boolean active) {
        return new RoleRule("r", unit, null, position, null, role, active);
    }

    @Test
    void aDepartmentRuleCoversTheDepartmentAndEverythingBelowIt() {
        List<RoleRule> rules = List.of(rule("tech", null, "employee", true));
        // the employee's own department is "test", which sits under "tech" under "root"
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("test", "tech", "root"), "p1")).containsExactly("EMPLOYEE");
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("hr", "root"), "p1")).isEmpty();
    }

    @Test
    void aPositionRuleAppliesInAnyDepartment() {
        List<RoleRule> rules = List.of(rule(null, "tl", "TEAM_LEAD", true));
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("a"), "tl")).containsExactly("TEAM_LEAD");
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("b"), "tl")).containsExactly("TEAM_LEAD");
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("a"), "se")).isEmpty();
    }

    @Test
    void aRuleWithBothConditionsNeedsBoth() {
        List<RoleRule> rules = List.of(rule("tech", "tl", "TECH_LEAD", true));
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("tech"), "tl")).containsExactly("TECH_LEAD");
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("tech"), "se")).isEmpty();
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("hr"), "tl")).isEmpty();
    }

    @Test
    void everyMatchingRuleContributesOnceAndInactiveOnesNever() {
        List<RoleRule> rules = List.of(
                rule("tech", null, "employee", true),
                rule(null, "tl", "Team_Lead", true),
                rule("tech", "tl", "EMPLOYEE", true),
                rule("tech", null, "AUDITOR", false));
        assertThat(RoleRuleMatcher.rolesFor(rules, Set.of("tech"), "tl")).containsExactly("EMPLOYEE", "TEAM_LEAD");
    }

    @Test
    void aRuleWithNeitherConditionNeverMatchesAnyone() {
        assertThat(RoleRuleMatcher.rolesFor(List.of(rule(null, null, "ADMIN", true)), Set.of("tech"), "tl")).isEmpty();
    }
}
