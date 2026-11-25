package com.heuristiq.identity.registration;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.IdentityRegisterRequest;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.notification.NmsNotificationService;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Registration stores the parsed international form and refuses a number that already belongs to someone. */
class UserRegistrationPhoneTest {
    private final UamUserRepository repository = mock(UamUserRepository.class);
    private final NmsNotificationService nms = mock(NmsNotificationService.class);
    private final UserRegistrationService service = new UserRegistrationService(repository, nms);

    @BeforeEach
    void saveReturnsWhatItIsGiven() {
        when(repository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByPhone(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void register(String mobile) {
        service.register(new UserRegistrationServiceUseCase.RegisterInput(
                new IdentityRegisterRequest("Jane", mobile, "jane@example.com", null, UamEnums.UserType.CUSTOMER)));
    }

    @Test
    void anInternationalNumberIsStoredAsCountryCodeAndNationalNumber() {
        register("+14155552671");

        ArgumentCaptor<UamUser> saved = ArgumentCaptor.forClass(UamUser.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPhoneNumber().getCountryCode()).isEqualTo("1");
        assertThat(saved.getValue().getPhoneNumber().getNumber()).isEqualTo("4155552671");
    }

    @Test
    void aBareIndianNumberIsReadInTheDefaultRegion() {
        register("9876543210");

        ArgumentCaptor<UamUser> saved = ArgumentCaptor.forClass(UamUser.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPhoneNumber().getCountryCode()).isEqualTo("91");
        assertThat(saved.getValue().getPhoneNumber().getNumber()).isEqualTo("9876543210");
    }

    @Test
    void aNumberAlreadyOnALegacyRowIsAConflict() {
        UamUser legacy = UamUser.builder().id("u-1").phoneNumber(new RPhoneNumber(null, "9876543210")).build();
        when(repository.findByPhone("+919876543210")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> register("+919876543210"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
        verify(repository, never()).save(any());
    }

    @Test
    void anUnparsableNumberIsABadRequest() {
        assertThatThrownBy(() -> register("12ab")).isInstanceOfSatisfying(UamException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(400));
        verify(repository, never()).save(any());
    }
}
