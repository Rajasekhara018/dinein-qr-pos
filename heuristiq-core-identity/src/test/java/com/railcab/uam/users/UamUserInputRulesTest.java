package com.heuristiq.uam.users;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertUserRequest;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.metrics.UamMetrics;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.repository.UamUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The user service refuses a weak password or a malformed email before anything is stored. */
class UamUserInputRulesTest {

    private UamUserRepository repository;
    private UserCredentialServiceUseCase credentials;
    private IdentityProviderSyncService idp;
    private UamUserService service;
    private int policyMinimum;

    @BeforeEach
    void setUp() {
        repository = mock(UamUserRepository.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        idp = mock(IdentityProviderSyncService.class);
        policyMinimum = 8;
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new UamUserService(repository, credentials, idp, mock(UamRoleRepository.class),
                new UamMetrics(new SimpleMeterRegistry()), tenantId -> policyMinimum);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static UpsertUserRequest request(String password, String email) {
        return new UpsertUserRequest("jane", password, "Jane Doe", null, email, "9876543210", List.of(), null);
    }

    @Test
    void createRefusesAOneCharacterPasswordAndStoresNothing() {
        assertThatThrownBy(() -> service.create(new UamUserServiceUseCase.CreateInput(request("a", "jane@example.com"))))
                .isInstanceOf(UamException.class).hasMessageContaining("at least 8");
        verify(repository, never()).save(any());
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void createRefusesAPasswordWithoutALetterAndADigit() {
        assertThatThrownBy(() -> service.create(new UamUserServiceUseCase.CreateInput(request("abcdefghij", "jane@example.com"))))
                .isInstanceOf(UamException.class).hasMessageContaining("letter and one digit");
        assertThatThrownBy(() -> service.create(new UamUserServiceUseCase.CreateInput(request("1234567890", "jane@example.com"))))
                .isInstanceOf(UamException.class).hasMessageContaining("letter and one digit");
        verify(repository, never()).save(any());
    }

    @Test
    void createRefusesAMalformedEmail() {
        for (String bad : List.of("bad@", "no-at", "a@b", "a b@example.com")) {
            assertThatThrownBy(() -> service.create(new UamUserServiceUseCase.CreateInput(request("Secret#123", bad)))).as(bad)
                    .isInstanceOf(UamException.class).hasMessageContaining("valid email");
        }
        verify(repository, never()).save(any());
    }

    @Test
    void createAcceptsAGoodPasswordAndEmail() {
        service.create(new UamUserServiceUseCase.CreateInput(request("Secret#123", "jane@example.com")));

        verify(repository).save(any());
        verify(credentials).setPassword(any());
    }

    @Test
    void thePolicyMinimumOfTheCallersOrganizationApplies() {
        TenantContext.set("acme");
        policyMinimum = 14;

        assertThatThrownBy(() -> service.create(new UamUserServiceUseCase.CreateInput(request("Secret#123", "jane@example.com"))))
                .isInstanceOf(UamException.class).hasMessageContaining("at least 14");
        service.create(new UamUserServiceUseCase.CreateInput(request("Secret#123456789", "jane@example.com")));
        verify(repository).save(any());
    }

    @Test
    void updateChecksAnEmailAndOnlyAChangedPassword() {
        UamUser existing = UamUser.builder().id("u1").userName("jane").build();
        when(repository.findById("u1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(new UamUserServiceUseCase.UpdateInput("u1", request(null, "bad@"))))
                .isInstanceOf(UamException.class).hasMessageContaining("valid email");
        assertThatThrownBy(() -> service.update(new UamUserServiceUseCase.UpdateInput("u1", request("x", "jane@example.com"))))
                .isInstanceOf(UamException.class).hasMessageContaining("at least 8");
        verify(repository, never()).save(any());

        service.update(new UamUserServiceUseCase.UpdateInput("u1", request(null, "jane@example.com")));
        verify(repository).save(any());
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void anAdministratorSettingAPasswordGetsTheSameRulesWithTheAccountsOrganization() {
        UamUser existing = UamUser.builder().id("u1").userName("jane").tenantId("acme").build();
        when(repository.findById("u1")).thenReturn(Optional.of(existing));
        policyMinimum = 12;

        assertThatThrownBy(() -> service.setPassword(new UamUserServiceUseCase.SetPasswordInput("u1", "Short1", false)))
                .isInstanceOf(UamException.class).hasMessageContaining("at least 12");
        verify(credentials, never()).setPassword(any());

        service.setPassword(new UamUserServiceUseCase.SetPasswordInput("u1", "LongEnough123", true));
        verify(credentials).setPassword(any());
        assertThat(existing.getTenantId()).isEqualTo("acme");
    }
}
