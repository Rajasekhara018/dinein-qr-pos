package com.heuristiq.identity.permissions;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.dto.UpsertPermissionRequest;
import com.heuristiq.identity.feature.management.ManagementPageSupport;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.common.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PermissionService implements PermissionServiceUseCase {
    private final PermissionRepository permissionRepository;
    private final IdentityProviderSyncService identityProviderSyncService;

    public PermissionService(PermissionRepository permissionRepository,
                             IdentityProviderSyncService identityProviderSyncService) {
        this.permissionRepository = permissionRepository;
        this.identityProviderSyncService = identityProviderSyncService;
    }

    public CreateOutput create(CreateInput input) {
        UpsertPermissionRequest request = input.request();
        String code = normalizeCode(request.code());
        permissionRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw UamException.conflict("Permission already exists");
        });
        LocalDateTime now = LocalDateTime.now();
        Permission permission = Permission.builder()
                .code(code)
                .applicationCode(normalizeOptional(request.applicationCode()))
                .module(request.module())
                .resource(request.resource())
                .action(request.action())
                .displayName(normalizeOptional(request.displayName()))
                .description(normalizeOptional(request.description()))
                .active(true)
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
        Permission saved = permissionRepository.save(permission);
        identityProviderSyncService.syncPermission(saved);
        return new CreateOutput(saved);
    }

    public UpdateOutput update(UpdateInput input) {
        String id = input.id();
        UpsertPermissionRequest request = input.request();
        Permission permission = get(new GetInput(id)).permission();
        String code = normalizeCode(request.code());
        permissionRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw UamException.conflict("Permission already exists");
                });
        permission.setCode(code);
        permission.setApplicationCode(normalizeOptional(request.applicationCode()));
        permission.setModule(request.module());
        permission.setResource(request.resource());
        permission.setAction(request.action());
        permission.setDisplayName(normalizeOptional(request.displayName()));
        permission.setDescription(normalizeOptional(request.description()));
        if (permission.getAudit() != null) {
            permission.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        Permission saved = permissionRepository.save(permission);
        identityProviderSyncService.syncPermission(saved);
        return new UpdateOutput(saved);
    }

    public GetOutput get(GetInput input) {
        String id = input.id();
        return new GetOutput(permissionRepository.findById(id).orElseThrow(() -> UamException.notFound("Permission not found")));
    }

    public ListOutput list(ListInput input) {
        return new ListOutput(permissionRepository.findAll(Sort.by(Sort.Direction.ASC, "code")));
    }

    public ListPageOutput list(ListPageInput input) {
        int page = input.page();
        int size = input.size();
        return new ListPageOutput(PageResult.from(permissionRepository.findAll(ManagementPageSupport.page(page, size, "code"))));
    }

    public DeleteOutput delete(DeleteInput input) {
        String id = input.id();
        Permission permission = get(new GetInput(id)).permission();
        identityProviderSyncService.deletePermission(permission);
        permissionRepository.delete(permission);
        return new DeleteOutput();
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new UamException("Permission code is required");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
