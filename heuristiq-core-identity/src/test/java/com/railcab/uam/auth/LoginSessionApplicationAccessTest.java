package com.heuristiq.uam.auth;

import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.device.UserAgentParser;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.uam.security.JwtService;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginSessionApplicationAccessTest {
    private UserSessionRepository sessionRepository;
    private UserDeviceRepository deviceRepository;
    private JwtService jwtService;
    private AuthAuditService authAuditService;
    private ApplicationAccessServiceUseCase accessService;
    private TenantMembershipServiceUseCase membershipService;
    private TenantApplicationServiceUseCase tenantApplications;
    private UamProperties properties;
    private HttpServletRequest request;
    private com.heuristiq.uam.organization.OrgWideAccess orgWideAccess;
    private LoginSessionService service;
    private final UamUser user = UamUser.builder().id("u1").userName("asha").roles(List.of("CUSTOMER")).build();

    @BeforeEach
    void setUp() {
        sessionRepository = mock(UserSessionRepository.class);
        deviceRepository = mock(UserDeviceRepository.class);
        jwtService = mock(JwtService.class);
        JwtServiceStubs.noFranchises(jwtService, "jwt");
        authAuditService = mock(AuthAuditService.class);
        accessService = mock(ApplicationAccessServiceUseCase.class);
        membershipService = mock(TenantMembershipServiceUseCase.class);
        tenantApplications = mock(TenantApplicationServiceUseCase.class);
        properties = new UamProperties();
        request = mock(HttpServletRequest.class);
        orgWideAccess = mock(com.heuristiq.uam.organization.OrgWideAccess.class);
        com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase credentialService =
                mock(com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase.class);
        when(credentialService.findActiveCredential(any())).thenReturn(
                new com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase.FindActiveCredentialOutput(java.util.Optional.empty()));
        service = new LoginSessionService(mock(UamUserRepository.class), sessionRepository, deviceRepository,
                mock(RefreshTokenServiceUseCase.class), jwtService, properties, mock(UserAgentParser.class),
                accessService, authAuditService, membershipService, tenantApplications, request,
                orgWideAccess, mock(com.heuristiq.uam.organization.repository.TenantRepository.class),
                credentialService);
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-1");
        when(accessService.resolveAccessibleApplications(any()))
                .thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("RIDER")));
    }

    @Test
    void onlyAnOrganizationWideAccountMayAskForAnotherOrganizationAtSignIn() {
        when(request.getHeader("X-Organization-Code")).thenReturn("ACME");
        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn("MATRIMONY");

        when(orgWideAccess.isOrgWide(user)).thenReturn(false);
        assertThatThrownBy(() -> service.issue(user, "203.0.113.7", "agent", false)).isInstanceOf(UamException.class);
        verify(membershipService).resolveForLogin(user, null);

        org.mockito.Mockito.clearInvocations(membershipService);
        when(orgWideAccess.isOrgWide(user)).thenReturn(true);
        assertThatThrownBy(() -> service.issue(user, "203.0.113.7", "agent", false)).isInstanceOf(UamException.class);
        verify(membershipService).resolveForLogin(user, "ACME");
    }

    @Test
    void loginIsRefusedBeforeAnySessionIsCreatedWhenTheNamedApplicationIsNotGranted() {
        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn("MATRIMONY");

        assertThatThrownBy(() -> service.issue(user, "203.0.113.7", "agent", false))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(authAuditService).loginFailed("asha", "No access to application MATRIMONY");
        verifyNoInteractions(sessionRepository, deviceRepository);
        verifyNoTokenIssued();
    }

    @Test
    void whenSubscriptionsAreEnforcedAnApplicationTheOrganizationDoesNotUseIsRefused() {
        properties.getTenancy().setEnforceSubscriptions(true);
        when(accessService.resolveAccessibleApplications(any()))
                .thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("RIDER", "MATRIMONY")));
        when(tenantApplications.activeApplicationCodes("org-1")).thenReturn(List.of("RIDER"));
        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn("MATRIMONY");

        assertThatThrownBy(() -> service.issue(user, "203.0.113.7", "agent", false))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(sessionRepository, deviceRepository);
        verifyNoTokenIssued();
    }

    @Test
    void whenSubscriptionsAreNotEnforcedTheOrganizationsApplicationsDoNotMatter() {
        when(accessService.resolveAccessibleApplications(any()))
                .thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("RIDER")));
        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn("MATRIMONY");

        assertThatThrownBy(() -> service.issue(user, "203.0.113.7", "agent", false)).isInstanceOf(UamException.class);

        verifyNoInteractions(tenantApplications);
    }

    @Test
    void refreshIsRefusedWhenTheGrantWasRevoked() {
        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn("matrimony");
        var resolved = new LoginSessionService.ResolvedRefresh(user, null);

        assertThatThrownBy(() -> service.rotate(resolved)).isInstanceOf(UamException.class);

        verifyNoInteractions(sessionRepository);
        verifyNoTokenIssued();
    }

    private void verifyNoTokenIssued() {
        verify(jwtService, org.mockito.Mockito.never()).generate(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }
}
