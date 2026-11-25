package com.heuristiq.identity.application.client;

import com.heuristiq.identity.application.client.ApiClientServiceUseCase.CreateInput;
import com.heuristiq.identity.application.client.ApiClientServiceUseCase.UpdateInput;
import com.heuristiq.identity.application.client.repository.ApiClientRepository;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.security.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The service refuses bad client ids and redirect URIs before anything is stored, and takes the organization from the resolver. */
class ApiClientServiceRulesTest {

    private ApiClientRepository repository;
    private IdentityProviderSyncService idp;
    private ClientTenantResolver tenants;
    private ApiClientService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApiClientRepository.class);
        idp = mock(IdentityProviderSyncService.class);
        tenants = mock(ClientTenantResolver.class);
        PasswordService passwords = mock(PasswordService.class);
        when(passwords.hash(any())).thenReturn("hash");
        when(repository.save(any(ApiClient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenants.resolve(any())).thenAnswer(inv -> "resolved");
        service = new ApiClientService(repository, passwords, idp, tenants);
    }

    private static UpsertClientRequest request(String clientId, String tenantId, Set<String> redirectUris) {
        return new UpsertClientRequest(clientId, tenantId, null, "Name", null, UamEnums.ApiClientType.WEB,
                UamEnums.ClientAuthenticationMethod.CLIENT_SECRET, Set.of(UamEnums.GrantType.PASSWORD), Set.of("read"), redirectUris, true);
    }

    @Test
    void createRefusesAClientIdWithSpacesOrSymbolsAndStoresNothing() {
        for (String bad : List.of("my client", "client!", "ab")) {
            assertThatThrownBy(() -> service.create(new CreateInput(request(bad, null, Set.of())))).as(bad)
                    .isInstanceOf(UamException.class).hasMessageContaining("Client id");
        }
        verify(repository, never()).save(any());
        verify(idp, never()).syncClient(any());
        verify(idp, never()).syncClient(any(), any());
    }

    @Test
    void createRefusesAJavascriptRedirectUri() {
        assertThatThrownBy(() -> service.create(new CreateInput(request("web-app", null, Set.of("javascript:alert(1)")))))
                .isInstanceOf(UamException.class).hasMessageContaining("Redirect URI");
        verify(repository, never()).save(any());
    }

    @Test
    void createStoresTheOrganizationTheResolverDecidedNotTheOneTyped() {
        ApiClient saved = service.create(new CreateInput(request("web-app", "typed-by-user", new LinkedHashSet<>(List.of(" https://app.example.com/cb "))))).created().client();

        assertThat(saved.getTenantId()).isEqualTo("resolved");
        assertThat(saved.getRedirectUris()).containsExactly("https://app.example.com/cb");
        verify(tenants).resolve("typed-by-user");
    }

    @Test
    void aRefusedOrganizationStopsTheCreate() {
        when(tenants.resolve("made-up")).thenThrow(new UamException("Organization not found: made-up"));

        assertThatThrownBy(() -> service.create(new CreateInput(request("web-app", "made-up", Set.of()))))
                .isInstanceOf(UamException.class).hasMessageContaining("Organization not found");
        verify(repository, never()).save(any());
    }

    @Test
    void updateAppliesTheSameRules() {
        ApiClient existing = ApiClient.builder().id("c1").clientId("web-app").build();
        when(repository.findById("c1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(new UpdateInput("c1", request("bad id", null, Set.of()))))
                .isInstanceOf(UamException.class).hasMessageContaining("Client id");
        assertThatThrownBy(() -> service.update(new UpdateInput("c1", request("web-app", null, Set.of("http://evil.example.com/cb")))))
                .isInstanceOf(UamException.class).hasMessageContaining("https");
        verify(repository, never()).save(any());

        ApiClient out = service.update(new UpdateInput("c1", request("web-app", "t", Set.of("https://ok.example.com/cb")))).client();
        assertThat(out.getTenantId()).isEqualTo("resolved");
    }
}
