package com.heuristiq.uam.application.access;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.registration.UserRegistrationServiceUseCase;
import com.heuristiq.uam.usercredentials.UserCredential;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A customer who signed up with a one-time code has no password; activation can give them a temporary one, and nobody else. */
class IssueTemporaryPasswordTest {

    private UamUserRepository users;
    private UserCredentialServiceUseCase credentials;
    private ApplicationUserProvisioningService service;

    @BeforeEach
    void setUp() {
        users = mock(UamUserRepository.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        service = new ApplicationUserProvisioningService(users, mock(UserRegistrationServiceUseCase.class),
                mock(ApplicationAccessServiceUseCase.class), credentials, mock(TenantMembershipServiceUseCase.class),
                mock(com.heuristiq.uam.organization.repository.TenantRepository.class),
                mock(com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase.class));
    }

    private UamUser user(String id, UamEnums.UserStatus status, List<String> roles) {
        UamUser user = UamUser.builder().id(id).status(status).roles(roles).build();
        when(users.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private void passwordPresent(String userId, boolean present) {
        UserCredential credential = mock(UserCredential.class);
        if (present) {
            when(credential.getPasswordHash()).thenReturn("existing-password-hash");
        }
        when(credentials.findActiveCredential(new UserCredentialServiceUseCase.FindActiveCredentialInput(userId, UamEnums.AuthProvider.LOCAL)))
                .thenReturn(new UserCredentialServiceUseCase.FindActiveCredentialOutput(
                        present ? Optional.of(credential) : Optional.empty()));
    }

    @Test
    void anActiveCustomerWithoutAPasswordGetsATemporaryOne() {
        user("u1", UamEnums.UserStatus.ACTIVE, List.of("CUSTOMER"));
        passwordPresent("u1", false);

        var out = service.issueTemporaryPassword(new ApplicationUserProvisioningServiceUseCase.IssueTemporaryPasswordInput("u1"));

        assertThat(out.temporaryPassword()).hasSize(14).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[0-9].*").matches(".*[@#$%*!?].*");
        ArgumentCaptor<UserCredentialServiceUseCase.SetPasswordInput> set = ArgumentCaptor.forClass(UserCredentialServiceUseCase.SetPasswordInput.class);
        verify(credentials).setPassword(set.capture());
        assertThat(set.getValue().userId()).isEqualTo("u1");
        assertThat(set.getValue().rawPassword()).isEqualTo(out.temporaryPassword());
        assertThat(set.getValue().temporary()).isTrue();
    }

    @Test
    void anAccountThatAlreadyHasAPasswordIsNeverChanged() {
        user("u1", UamEnums.UserStatus.ACTIVE, List.of("CUSTOMER"));
        passwordPresent("u1", true);

        var out = service.issueTemporaryPassword(new ApplicationUserProvisioningServiceUseCase.IssueTemporaryPasswordInput("u1"));

        assertThat(out.temporaryPassword()).isNull();
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void aBackOfficeAccountIsRefused() {
        user("u1", UamEnums.UserStatus.ACTIVE, List.of("SUPER_ADMIN"));

        assertThatThrownBy(() -> service.issueTemporaryPassword(new ApplicationUserProvisioningServiceUseCase.IssueTemporaryPasswordInput("u1")))
                .isInstanceOf(UamException.class);
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void anInactiveAccountIsRefused() {
        user("u1", UamEnums.UserStatus.INACTIVE, List.of("CUSTOMER"));

        assertThatThrownBy(() -> service.issueTemporaryPassword(new ApplicationUserProvisioningServiceUseCase.IssueTemporaryPasswordInput("u1")))
                .isInstanceOf(UamException.class);
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void anUnknownAccountIsNotFound() {
        when(users.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueTemporaryPassword(new ApplicationUserProvisioningServiceUseCase.IssueTemporaryPasswordInput("nobody")))
                .isInstanceOf(UamException.class).hasMessage("User not found");
    }
}
