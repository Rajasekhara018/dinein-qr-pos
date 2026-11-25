package com.heuristiq.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class RolePrefixMatcherTest {

    private static final List<String> PREFIXES = List.of("FRANCHISE_OWNER", "franchise_agent");

    @Test
    void theCodeItselfAndCodeUnderscoreSuffixMatchIgnoringCase() {
        assertThat(RolePrefixMatcher.matches("FRANCHISE_OWNER", PREFIXES)).isTrue();
        assertThat(RolePrefixMatcher.matches("franchise_owner_matrimony", PREFIXES)).isTrue();
        assertThat(RolePrefixMatcher.matches(" FRANCHISE_AGENT_RIDE ", PREFIXES)).isTrue();
        assertThat(RolePrefixMatcher.matches("Franchise_Agent", PREFIXES)).isTrue();
    }

    @Test
    void lookAlikesAndEmptySuffixesDoNotMatch() {
        assertThat(RolePrefixMatcher.matches("FRANCHISE_OWNERSHIP_AUDITOR", PREFIXES)).isFalse();
        assertThat(RolePrefixMatcher.matches("FRANCHISE_AGENTS", PREFIXES)).isFalse();
        assertThat(RolePrefixMatcher.matches("FRANCHISE_OWNER_", PREFIXES)).isFalse();
        assertThat(RolePrefixMatcher.matches("MY_FRANCHISE_OWNER", PREFIXES)).isFalse();
        assertThat(RolePrefixMatcher.matches("FRANCHISE", PREFIXES)).isFalse();
    }

    @Test
    void nullBlankAndEmptyInputsNeverMatch() {
        assertThat(RolePrefixMatcher.matches(null, PREFIXES)).isFalse();
        assertThat(RolePrefixMatcher.matches("  ", PREFIXES)).isFalse();
        assertThat(RolePrefixMatcher.matches("FRANCHISE_OWNER", null)).isFalse();
        assertThat(RolePrefixMatcher.matches("FRANCHISE_OWNER", Arrays.asList(null, " "))).isFalse();
        assertThat(RolePrefixMatcher.matchesAny(null, PREFIXES)).isFalse();
        assertThat(RolePrefixMatcher.matchesAny(List.of("FRANCHISE_OWNER"), null)).isFalse();
        assertThat(RolePrefixMatcher.matchesAny(List.of("FRANCHISE_OWNER"), List.of())).isFalse();
    }

    @Test
    void anyOneMatchingRoleIsEnough() {
        assertThat(RolePrefixMatcher.matchesAny(Arrays.asList("CUSTOMER", null, "FRANCHISE_AGENT_FOOD"), PREFIXES)).isTrue();
        assertThat(RolePrefixMatcher.matchesAny(List.of("CUSTOMER", "SUPER_ADMIN"), PREFIXES)).isFalse();
    }
}
