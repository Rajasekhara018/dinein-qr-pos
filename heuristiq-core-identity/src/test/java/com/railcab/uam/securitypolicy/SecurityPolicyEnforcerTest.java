package com.heuristiq.uam.securitypolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.passwordsHistory.PasswordHistoryRepository;
import com.heuristiq.uam.security.PasswordService;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.usercredentials.UserCredential;
import com.heuristiq.uam.usercredentials.UserCredentialRepository;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicyEnforcerTest {
    final SecurityPolicyRepository policies = mock(SecurityPolicyRepository.class);
    final EffectiveSecurityPolicyResolver resolver = new EffectiveSecurityPolicyResolver(policies, mock(ApplicationRepository.class),
            new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()), new UamProperties());
    final UamUserRepository users = mock(UamUserRepository.class);
    final UserCredentialRepository credentials = mock(UserCredentialRepository.class);
    final UserSessionRepository sessions = mock(UserSessionRepository.class);
    final ApplicationAccessServiceUseCase applications = mock(ApplicationAccessServiceUseCase.class);
    final SecurityPolicyEnforcer enforcer = new SecurityPolicyEnforcer(resolver, users, credentials,
            mock(PasswordHistoryRepository.class), mock(PasswordService.class), sessions, applications);

    UserSession session() {
        var user = UamUser.builder().id("user").tenantId("org").status(UamEnums.UserStatus.ACTIVE).roles(List.of("OPS")).build();
        when(users.findById("user")).thenReturn(Optional.of(user));
        var session = UserSession.builder().sessionId("session").userId("user").active(true)
                .loginAt(LocalDateTime.now().minusMinutes(5)).lastActivityAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusHours(2))
                .metadata(enforcer.sessionMetadata(user, resolver.resolve("org", null))).build();
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(session));
        return session;
    }
    @Test void acceptsCurrentSessionButNeverAnotherUsersSession() {
        var session = session();
        assertThat(enforcer.requireSession("session", "user")).isSameAs(session);
        assertThatThrownBy(() -> enforcer.requireSession("session", "other")).hasMessageContaining("Session expired");
    }
    @Test void rejectsPolicyChangesAndRevokedSessions() {
        var session = session();
        when(policies.findByTenantIdAndApplicationId("org", null)).thenReturn(Optional.of(
                SecurityPolicy.builder().id("new-policy").tenantId("org").name("Changed").active(true).version(1L).build()));
        assertThatThrownBy(() -> enforcer.requireSession("session", "user")).hasMessageContaining("policy changed");
        session.setRevoked(true);
        assertThatThrownBy(() -> enforcer.requireSession("session", "user")).hasMessageContaining("Session expired");
    }
    @Test void idleAbsolutePasswordAndRoleChangesRequireNewLogin() {
        var session = session();
        session.setLastActivityAt(LocalDateTime.now().minusHours(2));
        assertThatThrownBy(() -> enforcer.requireSession("session", "user")).hasMessageContaining("Session expired");
        session.setLastActivityAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> enforcer.requireSession("session", "user")).hasMessageContaining("Session expired");
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(credentials.findByUserIdAndProvider("user", UamEnums.AuthProvider.LOCAL)).thenReturn(Optional.of(
                UserCredential.builder().passwordChangedAt(LocalDateTime.now()).build()));
        assertThatThrownBy(() -> enforcer.requireSession("session", "user")).hasMessageContaining("Session expired");
        when(credentials.findByUserIdAndProvider("user", UamEnums.AuthProvider.LOCAL)).thenReturn(Optional.empty());
        users.findById("user").orElseThrow().setRoles(List.of("ADMIN"));
        assertThatThrownBy(() -> enforcer.requireSession("session", "user")).hasMessageContaining("Session expired");
    }

    @Test void verifiedRecoveryBypassesMinimumAgeButStillEnforcesPasswordStrength() {
        session();
        when(applications.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of()));
        when(policies.findByTenantIdAndApplicationId("org", null)).thenReturn(Optional.of(SecurityPolicy.builder()
                .name("age").tenantId("org").passwordPolicy(com.heuristiq.uam.helper.PasswordPolicy.builder().minimumAgeDays(2).build()).build()));
        when(credentials.findByUserIdAndProvider("user", UamEnums.AuthProvider.LOCAL)).thenReturn(Optional.of(
                UserCredential.builder().passwordChangedAt(LocalDateTime.now()).build()));
        assertThatThrownBy(() -> enforcer.requirePassword("user", "NewPassword9", false)).hasMessageContaining("recently");
        assertThatCode(() -> enforcer.requirePassword("user", "NewPassword9", false, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> enforcer.requirePassword("user", "bad", false, true)).hasMessageContaining("length");
    }

    @Test void policyInvalidatedSessionsDoNotBlockSigningInUnderTheNewSessionLimit() {
        var old = session();
        when(sessions.findByUserIdAndActiveTrue("user")).thenReturn(List.of(old));
        when(policies.findByTenantIdAndApplicationId("org", null)).thenReturn(Optional.of(SecurityPolicy.builder()
                .id("new").name("Single session").tenantId("org")
                .sessionPolicy(com.heuristiq.uam.helper.SessionPolicy.builder().singleSessionOnly(true).build()).build()));
        var current = resolver.resolve("org", null);
        assertThatCode(() -> enforcer.requireSessionSlot("user", current)).doesNotThrowAnyException();
    }
}
