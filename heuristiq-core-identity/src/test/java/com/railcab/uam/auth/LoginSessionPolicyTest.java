package com.heuristiq.uam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.device.UserAgentParser;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.organization.OrgWideAccess;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.refreshToken.*;
import com.heuristiq.uam.security.*;
import com.heuristiq.uam.securitypolicy.*;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginSessionPolicyTest {
    final UserSessionRepository sessions = mock(UserSessionRepository.class);
    final SecurityPolicyEnforcer enforcement = mock(SecurityPolicyEnforcer.class);
    final UamProperties properties = new UamProperties();
    final EffectiveSecurityPolicyResolver resolver = new EffectiveSecurityPolicyResolver(mock(SecurityPolicyRepository.class),
            mock(ApplicationRepository.class), new ObjectMapper(), properties);
    final TenantMembershipServiceUseCase memberships = mock(TenantMembershipServiceUseCase.class);
    final RefreshTokenServiceUseCase refresh = mock(RefreshTokenServiceUseCase.class);
    final JwtService jwt = mock(JwtService.class);
    final UamUser user = UamUser.builder().id("user").userName("user").roles(List.of("OPS")).status(UamEnums.UserStatus.ACTIVE).build();
    LoginSessionService service() {
        var devices = mock(UserDeviceRepository.class);
        when(devices.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwt.sessionAccess(any(), any())).thenReturn(SessionAccess.unchanged(user.getRoles()));
        var apps = mock(ApplicationAccessServiceUseCase.class);
        when(apps.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of()));
        var credentials = mock(UserCredentialServiceUseCase.class);
        when(credentials.findActiveCredential(any())).thenReturn(new UserCredentialServiceUseCase.FindActiveCredentialOutput(Optional.empty()));
        when(enforcement.sessionMetadata(any(), any())).thenAnswer(i -> {
            EffectiveSecurityPolicy p = i.getArgument(1);
            return new HashMap<>(Map.of("policyTenantId", p.tenantId(), "policyFingerprint", p.fingerprint(), "policyApplicationIds", List.of()));
        });
        var service = new LoginSessionService(mock(UamUserRepository.class), sessions, devices, refresh, jwt, properties,
                new UserAgentParser(), apps, mock(AuthAuditService.class), memberships, mock(TenantApplicationServiceUseCase.class),
                mock(HttpServletRequest.class), mock(OrgWideAccess.class), mock(TenantRepository.class), credentials);
        ReflectionTestUtils.setField(service, "policyEnforcer", enforcement);
        ReflectionTestUtils.setField(service, "policyResolver", resolver);
        return service;
    }
    @Test void issuesSessionWithMetadataInTheFirstSaveAndBindsItsJwt() {
        var service = service();
        when(memberships.resolveForLogin(any(), any())).thenReturn("org");
        service.issue(user, "127.0.0.1", null, true);
        var captured = org.mockito.ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).save(captured.capture());
        var saved = captured.getValue();
        assertThat(saved.getMetadata().get("policyTenantId")).isEqualTo("org");
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getLoginAt().plusDays(properties.getAuth().getRefreshExpiryDays()));
        verify(enforcement).requireLogin(eq(user), any(), eq(true));
        verify(enforcement).requireSessionSlot(eq("user"), any());
        verify(jwt).generateSession(eq(user), eq(List.of()), eq("org"), eq(false), any(), eq(saved.getSessionId()));
    }
    @Test void organizationSwitchChecksNewPolicyWithoutExtendingTheOriginalAbsoluteExpiry() {
        var service = service();
        var old = UserSession.builder().sessionId("sid").userId("user").loginAt(LocalDateTime.now().minusHours(1))
                .lastActivityAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusHours(2)).mfaVerified(true)
                .metadata(new HashMap<>(Map.of("policyTenantId", "old-org"))).build();
        var originalExpiry = old.getExpiresAt();
        when(enforcement.requireSession("sid", "user")).thenReturn(old);
        when(sessions.findBySessionId("sid")).thenReturn(Optional.of(old));
        when(memberships.resolveForLogin(any(), any())).thenReturn("new-org");
        var token = RefreshToken.builder().sessionId("sid").build();
        when(refresh.rotate(any())).thenReturn(new RefreshTokenServiceUseCase.RotateOutput(
                RefreshToken.builder().tokenHash("hash").build(), "new-refresh", false));
        service.rotate(new LoginSessionService.ResolvedRefresh(user, token, "refresh"));
        assertThat(old.getMetadata().get("policyTenantId")).isEqualTo("new-org");
        assertThat(old.getExpiresAt()).isEqualTo(originalExpiry);
        verify(enforcement).requireLogin(eq(user), any(), eq(true));
    }
}
