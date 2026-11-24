package com.heuristiq.uam.permissions;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertPermissionRequest;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.permissions.PermissionServiceUseCase.CreateInput;
import com.heuristiq.uam.permissions.PermissionServiceUseCase.DeleteInput;
import com.heuristiq.uam.permissions.PermissionServiceUseCase.GetInput;
import com.heuristiq.uam.permissions.PermissionServiceUseCase.ListInput;
import com.heuristiq.uam.permissions.PermissionServiceUseCase.ListPageInput;
import com.heuristiq.uam.permissions.PermissionServiceUseCase.UpdateInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionServiceTest {
    private PermissionRepository repository;
    private IdentityProviderSyncService idp;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        repository = mock(PermissionRepository.class);
        idp = mock(IdentityProviderSyncService.class);
        when(repository.save(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PermissionService(repository, idp);
    }

    private static UpsertPermissionRequest request(String code) {
        return new UpsertPermissionRequest(code, " APP ", UamEnums.ModuleCategory.values()[0],
                UamEnums.ResourceType.MASTER, UamEnums.PermissionAction.CREATE, " Create thing ", "  ");
    }

    @Test
    void createNormalizesCodeSavesAndSyncs() {
        Permission saved = service.create(new CreateInput(request(" user_create "))).permission();

        assertThat(saved.getCode()).isEqualTo("USER_CREATE");
        assertThat(saved.getApplicationCode()).isEqualTo("APP");
        assertThat(saved.getDisplayName()).isEqualTo("Create thing");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getActive()).isTrue();
        verify(idp).syncPermission(saved);
    }

    @Test
    void createRejectsBlankAndDuplicateCodes() {
        assertThatThrownBy(() -> service.create(new CreateInput(request(" "))))
                .hasMessageContaining("code is required");
        when(repository.findByCodeIgnoreCase("DUP")).thenReturn(Optional.of(Permission.builder().id("x").build()));
        assertThatThrownBy(() -> service.create(new CreateInput(request("dup"))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
    }

    @Test
    void updateChangesFieldsAndAllowsOwnCode() {
        Permission existing = Permission.builder().id("p1").code("OLD").audit(AuditInfo.builder().build()).build();
        when(repository.findById("p1")).thenReturn(Optional.of(existing));
        when(repository.findByCodeIgnoreCase("NEW")).thenReturn(Optional.of(existing));

        Permission out = service.update(new UpdateInput("p1", request("new"))).permission();

        assertThat(out.getCode()).isEqualTo("NEW");
        assertThat(out.getAudit().getUpdatedAt()).isNotNull();
        verify(idp).syncPermission(out);
    }

    @Test
    void updateRejectsCodeOfAnotherPermissionAndMissingRecord() {
        when(repository.findById("p1")).thenReturn(Optional.of(Permission.builder().id("p1").build()));
        when(repository.findByCodeIgnoreCase("TAKEN")).thenReturn(Optional.of(Permission.builder().id("p2").build()));
        assertThatThrownBy(() -> service.update(new UpdateInput("p1", request("taken"))))
                .hasMessageContaining("already exists");

        when(repository.findById("none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(new UpdateInput("none", request("x"))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateWorksWithoutAudit() {
        Permission existing = Permission.builder().id("p1").build();
        when(repository.findById("p1")).thenReturn(Optional.of(existing));
        assertThat(service.update(new UpdateInput("p1", request("c"))).permission().getCode()).isEqualTo("C");
    }

    @Test
    void listVariantsAndGet() {
        Permission p = Permission.builder().id("p").build();
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(p));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(p)));
        when(repository.findById("p")).thenReturn(Optional.of(p));

        assertThat(service.list(new ListInput()).permissions()).containsExactly(p);
        assertThat(service.list(new ListPageInput(0, 20)).permissions().content()).containsExactly(p);
        assertThat(service.get(new GetInput("p")).permission()).isSameAs(p);
    }

    @Test
    void deleteRemovesFromIdentityProviderThenRepository() {
        Permission p = Permission.builder().id("p").code("X").build();
        when(repository.findById("p")).thenReturn(Optional.of(p));

        service.delete(new DeleteInput("p"));

        verify(idp).deletePermission(p);
        verify(repository).delete(p);
    }
}
