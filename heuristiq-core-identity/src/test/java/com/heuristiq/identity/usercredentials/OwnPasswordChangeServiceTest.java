package com.heuristiq.identity.usercredentials;

import com.heuristiq.identity.common.UamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A person changes their own password: the current one must be right, and the temporary-password flag is cleared. */
class OwnPasswordChangeServiceTest {

    private UserCredentialServiceUseCase credentials;
    private OwnPasswordChangeService service;

    @BeforeEach
    void setUp() {
        credentials = mock(UserCredentialServiceUseCase.class);
        service = new OwnPasswordChangeService(credentials);
    }

    private void currentPasswordIs(boolean correct) {
        when(credentials.verifyPassword(new UserCredentialServiceUseCase.VerifyPasswordInput("u1", "Temp#1234abcd")))
                .thenReturn(new UserCredentialServiceUseCase.VerifyPasswordOutput(correct));
    }

    @Test
    void theNewPasswordIsSetAsNotTemporaryWhenTheCurrentOneIsRight() {
        currentPasswordIs(true);

        service.change("u1", "Temp#1234abcd", "Brand#New5678");

        verify(credentials).setPassword(new UserCredentialServiceUseCase.SetPasswordInput("u1", "Brand#New5678", false));
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        currentPasswordIs(false);

        assertThatThrownBy(() -> service.change("u1", "Temp#1234abcd", "Brand#New5678"))
                .isInstanceOf(UamException.class).hasMessage("Current password is incorrect");
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void theSamePasswordAgainIsRefused() {
        currentPasswordIs(true);

        assertThatThrownBy(() -> service.change("u1", "Temp#1234abcd", "Temp#1234abcd"))
                .isInstanceOf(UamException.class).hasMessage("The new password must be different from the current one");
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void missingValuesAreRefusedBeforeAnythingIsChecked() {
        assertThatThrownBy(() -> service.change("u1", " ", "Brand#New5678")).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.change("u1", "Temp#1234abcd", null)).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.change(null, "Temp#1234abcd", "Brand#New5678")).isInstanceOf(UamException.class);
        verify(credentials, never()).verifyPassword(any());
    }
}
