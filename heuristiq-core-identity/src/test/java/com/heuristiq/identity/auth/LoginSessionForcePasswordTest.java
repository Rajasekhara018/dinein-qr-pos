package com.heuristiq.identity.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.identity.audit.authevent.AuthAuditService;
import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.device.UserAgentParser;
import com.heuristiq.identity.device.UserDevice;
import com.heuristiq.identity.device.repository.UserDeviceRepository;
import com.heuristiq.identity.dto.LoginResponse;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import com.heuristiq.identity.organization.OrgWideAccess;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.refreshToken.RefreshToken;
import com.heuristiq.identity.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.identity.security.JwtService;
import com.heuristiq.identity.session.repository.UserSessionRepository;
import com.heuristiq.identity.usercredentials.UserCredential;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.FindActiveCredentialInput;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.FindActiveCredentialOutput;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sign-in / refresh response tells the client when the account still has a temporary password, so the app can make the
 * person choose their own before anything else. The flag comes from the user's own (local) credential, never from the request.
 */
class LoginSessionForcePasswordTest {
    private final UamUser user = UamUser.builder().id("u1").userName("asha").roles(new ArrayList<>(List.of("CUSTOMER"))).build();
    private UserCredentialServiceUseCase credentialService;
    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
        UserDeviceRepository deviceRepository = mock(UserDeviceRepository.class);
        JwtService jwtService = mock(JwtService.class);
        ApplicationAccessServiceUseCase accessService = mock(ApplicationAccessServiceUseCase.class);
        TenantMembershipServiceUseCase membershipService = mock(TenantMembershipServiceUseCase.class);
        credentialService = mock(UserCredentialServiceUseCase.class);
        when(deviceRepository.save(any())).thenAnswer(call -> {
            UserDevice device = call.getArgument(0);
            device.setId("d1");
            return device;
        });
        when(sessionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessionRepository.findBySessionId(any())).thenReturn(Optional.empty());
        JwtServiceStubs.noFranchises(jwtService, "jwt");
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-1");
        when(accessService.resolveAccessibleApplications(any()))
                .thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("MATRIMONY")));
        service = new LoginSessionService(mock(UamUserRepository.class), sessionRepository, deviceRepository,
                RotatingRefreshTokens.mockService(), jwtService, new UamProperties(), new UserAgentParser(),
                accessService, mock(AuthAuditService.class), membershipService,
                mock(TenantApplicationServiceUseCase.class), mock(HttpServletRequest.class), mock(OrgWideAccess.class),
                mock(TenantRepository.class), credentialService);
    }

    private void credentialWithFlag(Boolean force) {
        UserCredential credential = UserCredential.builder().userId("u1").provider(AuthProvider.LOCAL).forcePasswordChange(force).build();
        when(credentialService.findActiveCredential(new FindActiveCredentialInput("u1", AuthProvider.LOCAL)))
                .thenReturn(new FindActiveCredentialOutput(Optional.of(credential)));
    }

    @Test
    void signInWithATemporaryPasswordAsksForAChange() {
        credentialWithFlag(true);

        assertThat(service.issue(user, "203.0.113.7", "agent", false).forcePasswordChange()).isTrue();
    }

    @Test
    void signInWithAnOwnPasswordDoesNot() {
        credentialWithFlag(false);

        assertThat(service.issue(user, "203.0.113.7", "agent", false).forcePasswordChange()).isFalse();
    }

    @Test
    void anUnsetFlagOrNoLocalPasswordMeansNoChangeIsAsked() {
        credentialWithFlag(null);
        assertThat(service.issue(user, "203.0.113.7", "agent", false).forcePasswordChange()).isFalse();

        when(credentialService.findActiveCredential(new FindActiveCredentialInput("u1", AuthProvider.LOCAL)))
                .thenReturn(new FindActiveCredentialOutput(Optional.empty()));
        assertThat(service.issue(user, "203.0.113.7", "agent", false).forcePasswordChange()).isFalse();
    }

    @Test
    void aRefreshKeepsAskingUntilThePasswordIsChanged() {
        credentialWithFlag(true);
        RefreshToken token = RefreshToken.builder().sessionId("s1").build();

        LoginResponse response = service.rotate(new LoginSessionService.ResolvedRefresh(user, token, "raw"));

        assertThat(response.forcePasswordChange()).isTrue();
    }

    @Test
    void theFlagIsInTheJsonUnderTheNameTheClientsRead() {
        credentialWithFlag(true);

        var node = new ObjectMapper().valueToTree(service.issue(user, "203.0.113.7", "agent", false));

        assertThat(node.get("forcePasswordChange").asBoolean()).isTrue();
    }
}
