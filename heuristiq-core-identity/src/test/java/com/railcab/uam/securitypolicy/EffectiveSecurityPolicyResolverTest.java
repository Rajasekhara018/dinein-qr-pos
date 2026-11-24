package com.heuristiq.uam.securitypolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.helper.*;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EffectiveSecurityPolicyResolverTest {
    final SecurityPolicyRepository repository = mock(SecurityPolicyRepository.class);
    final ApplicationRepository applications = mock(ApplicationRepository.class);
    final EffectiveSecurityPolicyResolver resolver = new EffectiveSecurityPolicyResolver(repository, applications,
            new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()), new UamProperties());

    SecurityPolicy policy(String id, String tenant, String app) {
        var policy = SecurityPolicy.builder().id(id).name(id).tenantId(tenant).applicationId(app).active(true).version(1L).build();
        when(repository.findByTenantIdAndApplicationId(tenant, app)).thenReturn(Optional.of(policy));
        return policy;
    }

    @Test void composesEveryScopeWithoutAllowingChildToWeakenParent() {
        policy("platform", null, null).setPasswordPolicy(PasswordPolicy.builder().minimumLength(14).build());
        var org = policy("organization", "org", null);
        org.setPasswordPolicy(PasswordPolicy.builder().minimumLength(10).build());
        org.setMfaPolicy(MfaPolicy.builder().mandatory(true).build());
        policy("app", null, "app").setSessionPolicy(SessionPolicy.builder().idleTimeoutMinutes(12).build());
        var child = policy("org-app", "org", "app");
        child.setPasswordPolicy(PasswordPolicy.builder().minimumLength(16).build());
        child.setMfaPolicy(MfaPolicy.builder().mandatory(false).build());
        child.setSessionPolicy(SessionPolicy.builder().idleTimeoutMinutes(30).build());
        var result = resolver.resolve("org", "app");
        assertThat(result.number("passwordPolicy.minimumLength")).isEqualTo(16);
        assertThat(result.enabled("mfaPolicy.mandatory")).isTrue();
        assertThat(result.enabled("mfaPolicy.enabled")).isTrue();
        assertThat(result.number("sessionPolicy.idleTimeoutMinutes")).isEqualTo(12);
        assertThat(result.settings().get("passwordPolicy.minimumLength").sources()).containsExactly("org-app");
        assertThat(result.policies()).hasSize(4);
    }

    @Test void inactiveAndScheduledPoliciesDoNotApplyUntilActive() {
        var policy = policy("p", "org", null);
        policy.setPasswordPolicy(PasswordPolicy.builder().minimumLength(20).build());
        policy.setActive(false);
        assertThat(resolver.resolve("org", null).number("passwordPolicy.minimumLength")).isEqualTo(8);
        policy.setActive(true);
        policy.setEffectiveFrom(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        assertThat(resolver.resolve("org", null).policies()).isEmpty();
        policy.setEffectiveFrom(null);
        policy.setEffectiveUntil(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        assertThat(resolver.resolve("org", null).policies()).isEmpty();
    }

    @Test void versionChangesInvalidateFingerprintAndOtherOrganizationsStayIsolated() {
        var policy = policy("p", "org", null);
        var first = resolver.resolve("org", null);
        policy.setVersion(2L);
        assertThat(resolver.resolve("org", null).fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(resolver.resolve("other", null).policies()).isEmpty();
    }

    @Test void rejectsEffectiveConflictsAndUnknownApplicationCodes() {
        policy("global", null, null).setPasswordPolicy(PasswordPolicy.builder().minimumLength(20).build());
        policy("child", "org", null).setPasswordPolicy(PasswordPolicy.builder().maximumLength(12).build());
        assertThatThrownBy(() -> resolver.resolve("org", null)).hasMessageContaining("conflicting");
        assertThatThrownBy(() -> resolver.forApplications(null, java.util.List.of("UNKNOWN"))).hasMessageContaining("unknown application");
    }

    @Test void parentChangesAreCheckedAgainstDescendantsAndFutureSchedules() {
        var child = policy("child", "org", "app");
        child.setPasswordPolicy(PasswordPolicy.builder().maximumLength(12).build());
        child.setEffectiveFrom(LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        when(repository.findAll()).thenReturn(java.util.List.of(child));
        var draft = SecurityPolicy.builder().name("Stricter baseline").active(true)
                .passwordPolicy(PasswordPolicy.builder().minimumLength(20).build()).build();
        assertThatThrownBy(() -> resolver.validateChange(draft)).hasMessageContaining("conflicting");
    }
}
