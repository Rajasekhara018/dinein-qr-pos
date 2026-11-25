package com.heuristiq.identity.auth;

import com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.identity.audit.authevent.AuthAuditService;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.device.UserAgentParser;
import com.heuristiq.identity.device.repository.UserDeviceRepository;
import com.heuristiq.identity.organization.OrgWideAccess;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.refreshToken.RefreshToken;
import com.heuristiq.identity.refreshToken.RefreshTokenConstants;
import com.heuristiq.identity.refreshToken.RefreshTokenHashing;
import com.heuristiq.identity.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.identity.security.JwtService;
import com.heuristiq.identity.session.UserSession;
import com.heuristiq.identity.session.repository.UserSessionRepository;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The session side of a refresh: the presented raw token reaches rotation, and the session follows the family's current token. */
class LoginSessionRefreshTest {
    private UamUserRepository users;
    private UserSessionRepository sessions;
    private RefreshTokenServiceUseCase refreshTokens;
    private JwtService jwt;
    private LoginSessionService service;
    private final UamUser user = UamUser.builder().id("u1").userName("asha").roles(List.of("CUSTOMER")).build();
    private final RefreshToken presented = RefreshToken.builder().tokenId("t1").principalId("u1").sessionId("s1").build();

    @BeforeEach
    void setUp() {
        users = mock(UamUserRepository.class);
        sessions = mock(UserSessionRepository.class);
        refreshTokens = mock(RefreshTokenServiceUseCase.class);
        jwt = mock(JwtService.class);
        TenantMembershipServiceUseCase membership = mock(TenantMembershipServiceUseCase.class);
        ApplicationAccessServiceUseCase access = mock(ApplicationAccessServiceUseCase.class);
        UserCredentialServiceUseCase credentials = mock(UserCredentialServiceUseCase.class);
        when(credentials.findActiveCredential(any())).thenReturn(new UserCredentialServiceUseCase.FindActiveCredentialOutput(Optional.empty()));
        when(membership.resolveForLogin(any(), any())).thenReturn("org-1");
        when(access.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("RIDER")));
        JwtServiceStubs.noFranchises(jwt, "access-jwt");
        service = new LoginSessionService(users, sessions, mock(UserDeviceRepository.class), refreshTokens, jwt, new UamProperties(),
                mock(UserAgentParser.class), access, mock(AuthAuditService.class), membership,
                mock(TenantApplicationServiceUseCase.class), mock(HttpServletRequest.class), mock(OrgWideAccess.class),
                mock(TenantRepository.class), credentials);
    }

    @Test
    void resolveLooksUpTheHashOfTheRawTokenAndKeepsTheRawValue() {
        when(refreshTokens.findRedeemableByHash(new RefreshTokenServiceUseCase.FindRedeemableByHashInput(RefreshTokenHashing.sha256Hex("raw"))))
                .thenReturn(new RefreshTokenServiceUseCase.FindRedeemableByHashOutput(Optional.of(presented)));
        when(users.findById("u1")).thenReturn(Optional.of(user));

        LoginSessionService.ResolvedRefresh resolved = service.resolveForRefresh("raw").orElseThrow();

        assertThat(resolved.rawToken()).isEqualTo("raw");
        assertThat(resolved.token()).isSameAs(presented);
        assertThat(resolved.user()).isSameAs(user);
    }

    @Test
    void aLateReplayIsRefusedBeforeTheAccountIsEvenLoaded() {
        when(refreshTokens.findRedeemableByHash(new RefreshTokenServiceUseCase.FindRedeemableByHashInput(RefreshTokenHashing.sha256Hex("raw"))))
                .thenReturn(new RefreshTokenServiceUseCase.FindRedeemableByHashOutput(Optional.of(presented)));
        when(refreshTokens.revokeFamilyOnReuse(presented)).thenReturn(true);

        assertThat(service.resolveForRefresh("raw")).isEmpty();
        verify(refreshTokens).revokeFamilyOnReuse(presented);
        org.mockito.Mockito.verifyNoInteractions(users);
    }

    @Test
    void rotateHandsTheRawTokenToRotationAndReturnsTheSuccessorItGot() {
        RefreshToken successor = RefreshToken.builder().tokenId("t2").tokenHash("hash-2").build();
        when(refreshTokens.rotate(any())).thenReturn(new RefreshTokenServiceUseCase.RotateOutput(successor, "raw-2", true));
        UserSession session = UserSession.builder().sessionId("s1").refreshTokenHash("hash-1").build();
        when(sessions.findBySessionId("s1")).thenReturn(Optional.of(session));

        var response = service.rotate(new LoginSessionService.ResolvedRefresh(user, presented, "raw-1"));

        ArgumentCaptor<RefreshTokenServiceUseCase.RotateInput> input = ArgumentCaptor.forClass(RefreshTokenServiceUseCase.RotateInput.class);
        verify(refreshTokens).rotate(input.capture());
        assertThat(input.getValue().presented()).isSameAs(presented);
        assertThat(input.getValue().presentedRaw()).isEqualTo("raw-1");
        assertThat(input.getValue().newExpiresAt()).isNotNull();
        assertThat(response.refreshToken()).isEqualTo("raw-2");
        assertThat(session.getRefreshTokenHash()).isEqualTo("hash-2");
        verify(sessions).save(session);
    }

    @Test
    void aSessionUpdatedByAConcurrentRefreshIsNotAFailure() {
        when(refreshTokens.rotate(any())).thenReturn(new RefreshTokenServiceUseCase.RotateOutput(
                RefreshToken.builder().tokenHash("hash-2").build(), "raw-2", false));
        when(sessions.findBySessionId("s1")).thenReturn(Optional.of(UserSession.builder().sessionId("s1").build()));
        when(sessions.save(any())).thenThrow(new OptimisticLockingFailureException("version changed"));

        assertThat(service.rotate(new LoginSessionService.ResolvedRefresh(user, presented, "raw-1")).refreshToken()).isEqualTo("raw-2");
    }

    @Test
    void rotateWithoutThePresentedRawTokenIsRefused() {
        assertThatThrownBy(() -> service.rotate(new LoginSessionService.ResolvedRefresh(user, presented)))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getErrorCode()).isEqualTo(RefreshTokenConstants.ERROR_REFRESH_TOKEN_INVALID);
                });
        verify(refreshTokens, never()).rotate(any());
    }
}
