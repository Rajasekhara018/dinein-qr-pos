package com.heuristiq.uam.users;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertUserRequest;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Every stored account needs a phone number, so creating one without it must be refused with a clear 400 up front; before this check
 * the account was only rejected when the transaction committed, and the caller got a 500.
 */
class UamUserPhoneRequiredTest {

    private final UamUserRepository repository = mock(UamUserRepository.class);
    private final UamUserService service = new UamUserService(repository, mock(UserCredentialServiceUseCase.class),
            mock(IdentityProviderSyncService.class), mock(UamRoleRepository.class),
            new com.heuristiq.uam.metrics.UamMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    private UamUserServiceUseCase.CreateInput createWithPhone(String phone) {
        return new UamUserServiceUseCase.CreateInput(new UpsertUserRequest("jane.doe", "Secret#123", "Jane Doe", null,
                "jane.doe@example.com", phone, List.of(), null));
    }

    @Test
    void creatingAnAccountWithoutAPhoneNumberIsRefusedAsABadRequest() {
        assertThatThrownBy(() -> service.create(createWithPhone(null)))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("Phone number is required");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(repository, never()).save(any());
    }

    @Test
    void aBlankPhoneNumberIsRefusedToo() {
        assertThatThrownBy(() -> service.create(createWithPhone("   ")))
                .isInstanceOf(UamException.class)
                .hasMessage("Phone number is required");
        verify(repository, never()).save(any());
    }
}
