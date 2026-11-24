package com.heuristiq.uam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.device.UserAgentParser;
import com.heuristiq.uam.device.UserDevice;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;
import com.heuristiq.uam.organization.OrgWideAccess;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.uam.security.JwtService;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.usercredentials.UserCredential;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase.FindActiveCredentialInput;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase.FindActiveCredentialOutput;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
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
