package com.heuristiq.uam.application.client;

import com.heuristiq.uam.application.client.ApiClientServiceUseCase.CreateInput;
import com.heuristiq.uam.application.client.ApiClientServiceUseCase.DeleteInput;
import com.heuristiq.uam.application.client.ApiClientServiceUseCase.GetInput;
import com.heuristiq.uam.application.client.ApiClientServiceUseCase.ListInput;
import com.heuristiq.uam.application.client.ApiClientServiceUseCase.ListPageInput;
import com.heuristiq.uam.application.client.ApiClientServiceUseCase.RotateSecretInput;
import com.heuristiq.uam.application.client.ApiClientServiceUseCase.UpdateInput;
import com.heuristiq.uam.application.client.repository.ApiClientRepository;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.security.PasswordService;
import com.heuristiq.uam.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiClientServiceTest {
    private ApiClientRepository repository;
    private PasswordService passwords;
    private IdentityProviderSyncService idp;
    private ApiClientService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApiClientRepository.class);
        passwords = mock(PasswordService.class);
        idp = mock(IdentityProviderSyncService.class);
        when(repository.save(any(ApiClient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwords.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        service = new ApiClientService(repository, passwords, idp);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static UpsertClientRequest request(String clientId, Boolean active) {
        return new UpsertClientRequest(clientId, " t1 ", " ", " Name ", "desc", UamEnums.ApiClientType.WEB,
                UamEnums.ClientAuthenticationMethod.CLIENT_SECRET, Set.of(UamEnums.GrantType.PASSWORD),
                new LinkedHashSet<>(List.of(" read ", "", "write")), null, active);
    }

    @Test
    void createReturnsPlainSecretOnceAndStoresOnlyHash() {
        ApiClientCreated created = service.create(new CreateInput(request(" web ", null))).created();

        assertThat(created.clientSecret()).isNotBlank();
        assertThat(created.client().getClientSecretHash()).isEqualTo("hash:" + created.clientSecret());
        assertThat(created.client().getClientId()).isEqualTo("web");
        assertThat(created.client().getTenantId()).isEqualTo("t1");
        assertThat(created.client().getApplicationId()).isNull();
        assertThat(created.client().getName()).isEqualTo("Name");
        assertThat(created.client().getScopes()).containsExactly("read", "write");
        assertThat(created.client().getRedirectUris()).isEmpty();
        assertThat(created.client().getActive()).isTrue();
        verify(idp).syncClient(created.client(), created.clientSecret());
    }

    @Test
    void secretsDifferBetweenClients() {
        String a = service.create(new CreateInput(request("client-a", false))).created().clientSecret();
        String b = service.create(new CreateInput(request("client-b", false))).created().clientSecret();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void createRejectsBlankAndDuplicateClientId() {
        assertThatThrownBy(() -> service.create(new CreateInput(request(" ", null))))
                .hasMessageContaining("Client id is required");
        when(repository.findByClientIdIgnoreCase("dup")).thenReturn(Optional.of(ApiClient.builder().id("x").build()));
        assertThatThrownBy(() -> service.create(new CreateInput(request("dup", null))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
        verify(idp, never()).syncClient(any());
        verify(idp, never()).syncClient(any(), any());
    }

    @Test
    void updateChangesFieldsAndSyncs() {
        ApiClient existing = ApiClient.builder().id("c1").clientId("old").audit(AuditInfo.builder().build()).build();
        when(repository.findById("c1")).thenReturn(Optional.of(existing));
        when(repository.findByClientIdIgnoreCase("new")).thenReturn(Optional.of(existing));

        ApiClient out = service.update(new UpdateInput("c1", request("new", false))).client();

        assertThat(out.getClientId()).isEqualTo("new");
        assertThat(out.getActive()).isFalse();
        assertThat(out.getAudit().getUpdatedAt()).isNotNull();
        verify(idp).syncClient(out);
    }

    @Test
    void updateRejectsForeignClientIdAndToleratesMissingAudit() {
        ApiClient existing = ApiClient.builder().id("c1").build();
        when(repository.findById("c1")).thenReturn(Optional.of(existing));
        when(repository.findByClientIdIgnoreCase("taken")).thenReturn(Optional.of(ApiClient.builder().id("c2").build()));
        assertThatThrownBy(() -> service.update(new UpdateInput("c1", request("taken", null))))
                .hasMessageContaining("already exists");
        assertThat(service.update(new UpdateInput("c1", request("free", null))).client().getActive()).isTrue();
        assertThatThrownBy(() -> service.update(new UpdateInput("c1", request(" ", null))))
                .hasMessageContaining("Client id is required");
    }

    @Test
    void rotateSecretReplacesHashAndSyncs() {
        ApiClient existing = ApiClient.builder().id("c1").clientSecretHash("old").audit(AuditInfo.builder().build()).build();
        when(repository.findById("c1")).thenReturn(Optional.of(existing));

        String secret = service.rotateSecret(new RotateSecretInput("c1")).secret();

        assertThat(existing.getClientSecretHash()).isEqualTo("hash:" + secret);
        assertThat(existing.getAudit().getUpdatedAt()).isNotNull();
        verify(idp).syncClient(existing, secret);

        ApiClient noAudit = ApiClient.builder().id("c2").build();
        when(repository.findById("c2")).thenReturn(Optional.of(noAudit));
        assertThat(service.rotateSecret(new RotateSecretInput("c2")).secret()).isNotBlank();
    }

    @Test
    void getIsTenantScoped() {
        ApiClient foreign = ApiClient.builder().id("f").tenantId("orgB").build();
        when(repository.findById("f")).thenReturn(Optional.of(foreign));
        when(repository.findById("none")).thenReturn(Optional.empty());
        TenantContext.set("orgA");

        assertThatThrownBy(() -> service.get(new GetInput("f"))).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.rotateSecret(new RotateSecretInput("f"))).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.get(new GetInput("none")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        TenantContext.set("orgB");
        assertThat(service.get(new GetInput("f")).client()).isSameAs(foreign);
    }

    @Test
    void listVariants() {
        ApiClient c = ApiClient.builder().id("c").build();
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(c));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(c)));
        when(repository.search(eq("w"), eq(null), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(c, c)));
        when(repository.search(eq(null), eq(UamEnums.ApiClientType.WEB), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(c)));
        when(repository.search(eq(null), eq(null), eq(Boolean.FALSE), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list(new ListInput()).clients()).containsExactly(c);
        assertThat(service.list(new ListPageInput(0, 10)).clients().totalElements()).isEqualTo(1);
        assertThat(service.list(new ListPageInput(0, 10, "w", null, null)).clients().totalElements()).isEqualTo(2);
        assertThat(service.list(new ListPageInput(0, 10, null, UamEnums.ApiClientType.WEB, null)).clients().totalElements()).isEqualTo(1);
        assertThat(service.list(new ListPageInput(0, 10, null, null, false)).clients().empty()).isTrue();
    }

    @Test
    void deleteRemovesFromIdentityProviderAndRepositoryWithinTenant() {
        ApiClient c = ApiClient.builder().id("c").tenantId("orgA").build();
        when(repository.findById("c")).thenReturn(Optional.of(c));
        TenantContext.set("orgA");

        service.delete(new DeleteInput("c"));

        verify(idp).deleteClient(c);
        verify(repository).delete(c);

        TenantContext.set("orgB");
        assertThatThrownBy(() -> service.delete(new DeleteInput("c"))).isInstanceOf(UamException.class);
    }
}
