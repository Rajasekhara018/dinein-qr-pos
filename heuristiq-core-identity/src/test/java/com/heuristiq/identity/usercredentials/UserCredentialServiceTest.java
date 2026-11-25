package com.heuristiq.identity.usercredentials;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import com.heuristiq.identity.enums.UamEnums.PasswordChangeReason;
import com.heuristiq.identity.passwordsHistory.PasswordHistory;
import com.heuristiq.identity.passwordsHistory.PasswordHistoryRepository;
import com.heuristiq.identity.security.PasswordService;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.DeactivateInput;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.FindActiveCredentialInput;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.ListByUserInput;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.SetPasswordInput;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.VerifyPasswordInput;
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

class UserCredentialServiceTest {
    private UserCredentialRepository credentials;
    private PasswordHistoryRepository history;
    private PasswordService passwords;
    private UserCredentialService service;

    @BeforeEach
    void setUp() {
        credentials = mock(UserCredentialRepository.class);
        history = mock(PasswordHistoryRepository.class);
        passwords = new PasswordService();
        when(credentials.save(any(UserCredential.class))).thenAnswer(inv -> {
            UserCredential c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("cred-1");
            }
            return c;
        });
        service = new UserCredentialService(credentials, history, passwords);
    }

    @Test
    void verifyPasswordMatchesOnlyActiveLocalCredentialWithHash() {
        String hash = passwords.hash("Secret#123");
        when(credentials.findByUserIdAndProvider("u1", AuthProvider.LOCAL)).thenReturn(Optional.of(
                UserCredential.builder().active(true).passwordHash(hash).build()));
        assertThat(service.verifyPassword(new VerifyPasswordInput("u1", "Secret#123")).verified()).isTrue();
        assertThat(service.verifyPassword(new VerifyPasswordInput("u1", "wrong")).verified()).isFalse();

        when(credentials.findByUserIdAndProvider("u2", AuthProvider.LOCAL)).thenReturn(Optional.of(
                UserCredential.builder().active(false).passwordHash(hash).build()));
        assertThat(service.verifyPassword(new VerifyPasswordInput("u2", "Secret#123")).verified()).isFalse();

        when(credentials.findByUserIdAndProvider("u3", AuthProvider.LOCAL)).thenReturn(Optional.of(
                UserCredential.builder().active(true).passwordHash(" ").build()));
        assertThat(service.verifyPassword(new VerifyPasswordInput("u3", "x")).verified()).isFalse();

        when(credentials.findByUserIdAndProvider("u4", AuthProvider.LOCAL)).thenReturn(Optional.empty());
        assertThat(service.verifyPassword(new VerifyPasswordInput("u4", "x")).verified()).isFalse();
    }

    @Test
    void setPasswordCreatesCredentialHashesAndWritesHistory() {
        when(credentials.findByUserIdAndProvider("u1", AuthProvider.LOCAL)).thenReturn(Optional.empty());

        UserCredential saved = service.setPassword(new SetPasswordInput("u1", "Secret#123", false)).credential();

        assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(saved.getPrimaryCredential()).isTrue();
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getForcePasswordChange()).isFalse();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Secret#123");
        assertThat(passwords.matches("Secret#123", saved.getPasswordHash())).isTrue();
        ArgumentCaptor<PasswordHistory> captor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(history).save(captor.capture());
        assertThat(captor.getValue().getChangeReason()).isEqualTo(PasswordChangeReason.USER_CHANGED);
        assertThat(captor.getValue().getCredentialId()).isEqualTo("cred-1");
    }

    @Test
    void temporaryPasswordForcesChangeAndIsRecordedAsAdminReset() {
        UserCredential existing = UserCredential.builder().id("c9").userId("u1").active(false).build();
        when(credentials.findByUserIdAndProvider("u1", AuthProvider.LOCAL)).thenReturn(Optional.of(existing));

        UserCredential saved = service.setPassword(new SetPasswordInput("u1", "Temp#123", true)).credential();

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getForcePasswordChange()).isTrue();
        assertThat(saved.getActive()).isTrue();
        ArgumentCaptor<PasswordHistory> captor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(history).save(captor.capture());
        assertThat(captor.getValue().getChangeReason()).isEqualTo(PasswordChangeReason.ADMIN_RESET);
    }

    @Test
    void setPasswordRequiresUserAndPassword() {
        assertThatThrownBy(() -> service.setPassword(new SetPasswordInput(" ", "pw", false)))
                .hasMessageContaining("User id is required");
        assertThatThrownBy(() -> service.setPassword(new SetPasswordInput("u1", null, false)))
                .hasMessageContaining("Password is required");
        verify(credentials, never()).save(any());
        verify(history, never()).save(any());
    }

    @Test
    void findActiveListAndDeactivate() {
        UserCredential active = UserCredential.builder().id("a").active(true).build();
        UserCredential inactive = UserCredential.builder().id("b").active(false).build();
        when(credentials.findByUserIdAndProvider("u1", AuthProvider.LOCAL)).thenReturn(Optional.of(active));
        when(credentials.findByUserIdAndProvider("u2", AuthProvider.LOCAL)).thenReturn(Optional.of(inactive));
        assertThat(service.findActiveCredential(new FindActiveCredentialInput("u1", AuthProvider.LOCAL)).credential()).containsSame(active);
        assertThat(service.findActiveCredential(new FindActiveCredentialInput("u2", AuthProvider.LOCAL)).credential()).isEmpty();

        when(credentials.findByUserId("u1")).thenReturn(List.of(active, inactive));
        assertThat(service.listByUser(new ListByUserInput("u1")).credentials()).hasSize(2);

        when(credentials.findById("a")).thenReturn(Optional.of(active));
        when(credentials.findById("zz")).thenReturn(Optional.empty());
        service.deactivate(new DeactivateInput("a"));
        assertThat(active.getActive()).isFalse();
        assertThatThrownBy(() -> service.deactivate(new DeactivateInput("zz"))).isInstanceOf(UamException.class);
    }
}
