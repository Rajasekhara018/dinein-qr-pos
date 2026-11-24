package com.heuristiq.uam.users;

import com.heuristiq.uam.dto.UpsertUserRequest;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A user created by an organization's admin belongs to that organization, so search and lists can show it to them. */
class UamUserCreateTenantTest {

    private final UamUserRepository repository = mock(UamUserRepository.class);
    private final UamUserService service = new UamUserService(repository, mock(UserCredentialServiceUseCase.class),
            mock(IdentityProviderSyncService.class), mock(UamRoleRepository.class),
            new com.heuristiq.uam.metrics.UamMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    @BeforeEach
    void saveReturnsWhatItIsGiven() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UamUser created() {
        service.create(new UamUserServiceUseCase.CreateInput(new UpsertUserRequest("jane", "Secret#123", "Jane Doe", null,
                "jane@example.com", "9876543210", List.of(), null)));
        ArgumentCaptor<UamUser> saved = ArgumentCaptor.forClass(UamUser.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void theNewAccountBelongsToTheCallersOrganization() {
        TenantContext.set("org-7");

        UamUser user = created();

        assertThat(user.getTenantId()).isEqualTo("org-7");
        assertThat(user.getSearchIndexFields()).containsEntry("ownerKey", "org-7").containsEntry("text5", "org-7");
    }

    @Test
    void withoutASessionOrganizationTheAccountIsLeftWithoutOne() {
        assertThat(created().getTenantId()).isNull();
    }

    private void signInAs(String... authorities) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("u", null,
                        java.util.Arrays.stream(authorities).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void anOrganizationAdminCannotGiveTheSuperAdminRole() {
        signInAs("ROLE_ACME_ADMIN");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(new UamUserServiceUseCase.CreateInput(
                new UpsertUserRequest("jane", "Secret#123", "Jane Doe", null, "jane@example.com", "9876543210", List.of("SUPER_ADMIN"), null))))
                .isInstanceOf(com.heuristiq.uam.common.UamException.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void aSuperAdminMayGiveTheSuperAdminRole() {
        signInAs("ROLE_SUPER_ADMIN");

        service.create(new UamUserServiceUseCase.CreateInput(
                new UpsertUserRequest("jane", "Secret#123", "Jane Doe", null, "jane@example.com", "9876543210", List.of("SUPER_ADMIN"), null)));

        verify(repository).save(any());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
