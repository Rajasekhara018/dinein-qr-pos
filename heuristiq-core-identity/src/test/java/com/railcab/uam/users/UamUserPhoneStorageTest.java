package com.heuristiq.uam.users;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertUserRequest;
import com.heuristiq.uam.helper.RPhoneNumber;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
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

/** An admin-created account stores the parsed international phone and keeps one account per real number. */
class UamUserPhoneStorageTest {
    private final UamUserRepository repository = mock(UamUserRepository.class);
    private final UamUserService service = new UamUserService(repository, mock(UserCredentialServiceUseCase.class),
            mock(IdentityProviderSyncService.class), mock(UamRoleRepository.class),
            new com.heuristiq.uam.metrics.UamMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    @BeforeEach
    void saveReturnsWhatItIsGiven() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void create(String phone) {
        service.create(new UamUserServiceUseCase.CreateInput(new UpsertUserRequest("jane", "Secret#123", "Jane Doe", null,
                "jane@example.com", phone, List.of(), null)));
    }

    @Test
    void aUsNumberIsStoredInParsedForm() {
        create("+14155552671");

        ArgumentCaptor<UamUser> saved = ArgumentCaptor.forClass(UamUser.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo(new RPhoneNumber("1", "4155552671"));
    }

    @Test
    void aNumberHeldByAnotherAccountIsAConflict() {
        UamUser other = UamUser.builder().id("other").phoneNumber(new RPhoneNumber(null, "9876543210")).build();
        when(repository.findByPhone("+919876543210")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> create("+919876543210")).isInstanceOfSatisfying(UamException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(409));
        verify(repository, never()).save(any());
    }

    @Test
    void aGarbageNumberIsABadRequest() {
        assertThatThrownBy(() -> create("garbage")).isInstanceOfSatisfying(UamException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(400));
    }

    @Test
    void noPhoneIsRefusedBeforeAnythingIsSaved() {
        // Both stored user models require a phone number: an account without one used to be rejected only at commit (a 500).
        assertThatThrownBy(() -> create(null)).isInstanceOfSatisfying(UamException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(400));
        verify(repository, never()).save(any());
    }
}
