package com.heuristiq.identity.biometric;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.heuristiq.identity.biometric.application.BiometricRevocationUseCase;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.metrics.UamMetrics;
import com.heuristiq.identity.organization.PlatformOrganizationProperties;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.TenantMembership;
import com.heuristiq.identity.organization.membership.TenantMembershipService;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.membership.TenantMembershipStatus;
import com.heuristiq.identity.organization.membership.UpdateMemberRequest;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.passwordsHistory.PasswordHistoryRepository;
import com.heuristiq.identity.refreshToken.RefreshTokenController;
import com.heuristiq.identity.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.security.PasswordService;
import com.heuristiq.identity.session.UserSessionService;
import com.heuristiq.identity.session.UserSessionServiceUseCase;
import com.heuristiq.identity.session.repository.UserSessionRepository;
import com.heuristiq.identity.usercredentials.UserCredential;
import com.heuristiq.identity.usercredentials.UserCredentialRepository;
import com.heuristiq.identity.usercredentials.UserCredentialService;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.UamUserService;
import com.heuristiq.identity.users.UamUserServiceUseCase;
import com.heuristiq.identity.users.repository.UamUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Every revocation hook calls the one revocation use case with its reason: password change and reset, "log out of all
 * devices" (own and administrator), account suspension and deletion, and removal from an organization.
 */
class BiometricRevocationHooksTest {

    private static final String USER = "u1";

    private BiometricRevocationUseCase revocation;
    private ObjectProvider<BiometricRevocationUseCase> provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        revocation = mock(BiometricRevocationUseCase.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(revocation);
    }

    private UserCredentialService credentialService(UserCredentialRepository repository) {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new UserCredentialService(repository, mock(PasswordHistoryRepository.class), new PasswordService(), provider);
    }

    @Test
    void changingAnExistingPasswordRevokesWithPasswordChanged() {
        UserCredentialRepository repository = mock(UserCredentialRepository.class);
        when(repository.findByUserIdAndProvider(USER, UamEnums.AuthProvider.LOCAL))
                .thenReturn(Optional.of(UserCredential.builder().userId(USER).passwordHash("old-hash").build()));

        credentialService(repository).setPassword(new UserCredentialServiceUseCase.SetPasswordInput(USER, "New#Pass1", false));

        verify(revocation).revokeAllForUser(USER, BiometricRevocationReason.PASSWORD_CHANGED);
    }

    @Test
    void aForgotPasswordOrAdministratorResetRevokesWithPasswordReset() {
        UserCredentialRepository repository = mock(UserCredentialRepository.class);
        when(repository.findByUserIdAndProvider(USER, UamEnums.AuthProvider.LOCAL))
                .thenReturn(Optional.of(UserCredential.builder().userId(USER).passwordHash("old-hash").build()));
        UserCredentialService service = credentialService(repository);

        service.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(USER, "New#Pass1", false, true));
        service.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(USER, "Temp#Pass1", true));

        verify(revocation, org.mockito.Mockito.times(2)).revokeAllForUser(USER, BiometricRevocationReason.PASSWORD_RESET);
    }

    @Test
    void theFirstPasswordOfANewAccountRevokesNothing() {
        UserCredentialRepository repository = mock(UserCredentialRepository.class);
        when(repository.findByUserIdAndProvider(USER, UamEnums.AuthProvider.LOCAL)).thenReturn(Optional.empty());

        credentialService(repository).setPassword(new UserCredentialServiceUseCase.SetPasswordInput(USER, "New#Pass1", false));

        verify(revocation, never()).revokeAllForUser(any(), any());
    }

    @Test
    void logOutOfAllDevicesRevokesWithLogoutAll() {
        UserSessionRepository sessions = mock(UserSessionRepository.class);
        when(sessions.findByUserIdAndActiveTrue(USER)).thenReturn(List.of());

        new UserSessionService(sessions, provider).revokeAllForUser(new UserSessionServiceUseCase.RevokeAllForUserInput(USER));

        verify(revocation).revokeAllForUser(USER, BiometricRevocationReason.LOGOUT_ALL);
    }

    @Test
    void theAdministratorsRevokeAllTokensRevokesWithLogoutAll() {
        RefreshTokenServiceUseCase refreshTokens = mock(RefreshTokenServiceUseCase.class);

        new RefreshTokenController(refreshTokens, provider).revokeAll(USER);

        verify(revocation).revokeAllForUser(USER, BiometricRevocationReason.LOGOUT_ALL);
        verify(refreshTokens).revokeAllForPrincipal(new RefreshTokenServiceUseCase.RevokeAllForPrincipalInput(USER));
    }

    private UamUserService userService(UamUserRepository users) {
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        return new UamUserService(users, mock(UserCredentialServiceUseCase.class), mock(IdentityProviderSyncService.class),
                mock(UamRoleRepository.class), new UamMetrics(new SimpleMeterRegistry()), tenantId -> 8, provider);
    }

    @Test
    void suspendingAnAccountRevokesButReactivatingDoesNot() {
        UamUserRepository users = mock(UamUserRepository.class);
        when(users.findById(USER)).thenReturn(Optional.of(UamUser.builder().id(USER).userName("jane").status(UamEnums.UserStatus.ACTIVE).build()));
        UamUserService service = userService(users);

        service.updateStatus(new UamUserServiceUseCase.UpdateStatusInput(USER, UamEnums.UserStatus.ACTIVE));
        verify(revocation, never()).revokeAllForUser(any(), any());

        service.updateStatus(new UamUserServiceUseCase.UpdateStatusInput(USER, UamEnums.UserStatus.SUSPENDED));
        verify(revocation).revokeAllForUser(USER, BiometricRevocationReason.ACCOUNT_SUSPENDED);
    }

    @Test
    void deletingAnAccountRevokesWithAccountDeleted() {
        UamUserRepository users = mock(UamUserRepository.class);
        when(users.findById(USER)).thenReturn(Optional.of(UamUser.builder().id(USER).userName("jane").status(UamEnums.UserStatus.ACTIVE).build()));

        userService(users).delete(new UamUserServiceUseCase.DeleteInput(USER));

        verify(revocation).revokeAllForUser(USER, BiometricRevocationReason.ACCOUNT_DELETED);
    }

    @Test
    void removalFromAnOrganizationRevokesButSuspensionOfTheMembershipDoesNot() {
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById("org-a")).thenReturn(Optional.of(Tenant.builder().id("org-a").code("ORG_A").build()));
        TenantMembership membership = TenantMembership.builder().tenantId("org-a").userId(USER).status(TenantMembershipStatus.ACTIVE).build();
        when(memberships.findByTenantIdAndUserId("org-a", USER)).thenReturn(Optional.of(membership));
        when(memberships.save(any())).thenAnswer(i -> i.getArgument(0));
        TenantMembershipService service = new TenantMembershipService(memberships, tenants, mock(UamUserRepository.class),
                PlatformOrganizationProperties.defaults(), provider);

        service.updateMember(new TenantMembershipServiceUseCase.UpdateInput("org-a", USER,
                new UpdateMemberRequest(TenantMembershipStatus.SUSPENDED), "admin"));
        verify(revocation, never()).revokeAllForUser(any(), any());

        service.updateMember(new TenantMembershipServiceUseCase.UpdateInput("org-a", USER,
                new UpdateMemberRequest(TenantMembershipStatus.REMOVED), "admin"));
        verify(revocation).revokeAllForUser(USER, BiometricRevocationReason.MEMBERSHIP_REMOVED);
    }
}
