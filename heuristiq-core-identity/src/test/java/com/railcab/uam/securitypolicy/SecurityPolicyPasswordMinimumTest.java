package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The effective minimum password length: the organization's policy, else the platform's, else 8; never below 8. */
class SecurityPolicyPasswordMinimumTest {

    private SecurityPolicyRepository policies;
    private SecurityPolicyPasswordMinimum lookup;

    @BeforeEach
    void setUp() {
        policies = mock(SecurityPolicyRepository.class);
        when(policies.findByTenantIdAndApplicationId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        lookup = new SecurityPolicyPasswordMinimum(policies);
    }

    private static SecurityPolicy policyWithMinimum(Integer minimum) {
        return SecurityPolicy.builder().passwordPolicy(PasswordPolicy.builder().minimumLength(minimum).build()).build();
    }

    @Test
    void withNoPolicyTheDefaultApplies() {
        assertThat(lookup.minimumLength("acme")).isEqualTo(8);
        assertThat(lookup.minimumLength(null)).isEqualTo(8);
    }

    @Test
    void theOrganizationsPolicyWinsOverThePlatformOne() {
        when(policies.findByTenantIdAndApplicationId("acme", null)).thenReturn(Optional.of(policyWithMinimum(14)));
        when(policies.findByTenantIdAndApplicationId(null, null)).thenReturn(Optional.of(policyWithMinimum(10)));

        assertThat(lookup.minimumLength("acme")).isEqualTo(14);
        assertThat(lookup.minimumLength("other")).isEqualTo(10);
        assertThat(lookup.minimumLength(null)).isEqualTo(10);
    }

    @Test
    void aLoosePolicyCannotGoBelowTheFloor() {
        when(policies.findByTenantIdAndApplicationId(null, null)).thenReturn(Optional.of(policyWithMinimum(4)));

        assertThat(lookup.minimumLength("acme")).isEqualTo(8);
    }

    @Test
    void aPolicyWithoutAPasswordSectionFallsBackToTheDefault() {
        when(policies.findByTenantIdAndApplicationId(null, null)).thenReturn(Optional.of(SecurityPolicy.builder().build()));

        assertThat(lookup.minimumLength("acme")).isEqualTo(8);
    }
}
