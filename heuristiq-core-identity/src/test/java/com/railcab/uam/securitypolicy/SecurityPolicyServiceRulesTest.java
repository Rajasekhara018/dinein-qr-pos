package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.helper.LoginPolicy;
import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.helper.SessionPolicy;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Values captured from the form in the QA run (min 20 with max 8, -1 attempts, 0 idle minutes) never reach the store. */
class SecurityPolicyServiceRulesTest {

    private SecurityPolicyRepository repository;
    private SecurityPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(SecurityPolicyRepository.class);
        when(repository.save(any(SecurityPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SecurityPolicyService(repository);
    }

    private static UpsertSecurityPolicyRequest request(PasswordPolicy password, LoginPolicy login, SessionPolicy session) {
        return new UpsertSecurityPolicyRequest(null, null, "Default", null, password, login, session, null, null, null);
    }

    @Test
    void createRefusesTheQaValuesAndStoresNothing() {
        assertThatThrownBy(() -> service.create(new SecurityPolicyServiceUseCase.CreateInput(
                request(PasswordPolicy.builder().minimumLength(20).maximumLength(8).build(), null, null))))
                .isInstanceOf(UamException.class).hasMessageContaining("cannot be more than");
        assertThatThrownBy(() -> service.create(new SecurityPolicyServiceUseCase.CreateInput(
                request(null, LoginPolicy.builder().maxFailedAttempts(-1).build(), null))))
                .isInstanceOf(UamException.class).hasMessageContaining("Maximum failed attempts");
        assertThatThrownBy(() -> service.create(new SecurityPolicyServiceUseCase.CreateInput(
                request(null, null, SessionPolicy.builder().idleTimeoutMinutes(0).build()))))
                .isInstanceOf(UamException.class).hasMessageContaining("Idle timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void createStoresAValidPolicy() {
        SecurityPolicy saved = service.create(new SecurityPolicyServiceUseCase.CreateInput(request(
                PasswordPolicy.builder().minimumLength(10).maximumLength(64).build(),
                LoginPolicy.builder().maxFailedAttempts(5).build(), SessionPolicy.builder().idleTimeoutMinutes(30).build()))).policy();

        assertThat(saved.getPasswordPolicy().getMinimumLength()).isEqualTo(10);
    }

    @Test
    void updateAppliesTheSameRulesBeforeChangingAnything() {
        SecurityPolicy existing = SecurityPolicy.builder().id("p1").name("Default").build();
        when(repository.findById("p1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(new SecurityPolicyServiceUseCase.UpdateInput("p1",
                request(null, LoginPolicy.builder().maxFailedAttempts(0).build(), null))))
                .isInstanceOf(UamException.class);
        verify(repository, never()).save(any());
        assertThat(existing.getLoginPolicy()).isNull();
    }
}
