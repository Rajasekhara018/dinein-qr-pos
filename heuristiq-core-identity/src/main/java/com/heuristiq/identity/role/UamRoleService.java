package com.heuristiq.uam.role;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertRoleRequest;
import com.heuristiq.uam.feature.management.ManagementPageSupport;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.security.SecurityConstants;
import com.heuristiq.uam.security.TenantGuard;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.users.repository.UamUserSearchCriteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.heuristiq.uam.common.PageResult;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UamRoleService implements UamRoleServiceUseCase {
    private final UamRoleRepository roleRepository;
    private final IdentityProviderSyncService identityProviderSyncService;

    private final UamUserRepository userRepository;

    public UamRoleService(UamRoleRepository roleRepository, IdentityProviderSyncService identityProviderSyncService,
                          UamUserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.identityProviderSyncService = identityProviderSyncService;
        this.userRepository = userRepository;
    }

    public CreateOutput create(CreateInput input) {
        UpsertRoleRequest request = input.request();
        String code = normalizeCode(request.code());
        RoleRules.requireValidCode(code);
        RoleRules.requirePlatformAdminForSystemRole(request.systemRole(), TenantGuard.isSuperAdmin());
        List<String> permissions = normalizeCodes(request.permissions());
        requireCallerHolds(permissions);
        roleRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw UamException.conflict("Role already exists");
        });
        LocalDateTime now = LocalDateTime.now();
        // A role made by an organization's admin belongs to that organization; only a platform administrator creating a
        // system role makes one every organization can use.
        boolean shared = Boolean.TRUE.equals(request.systemRole()) && TenantGuard.isSuperAdmin();
        UamRole role = UamRole.builder()
                .tenantId(shared ? null : TenantContext.get())
                .code(code)
                .name(defaultName(request.name(), code))
                .description(normalizeOptional(request.description()))
                .permissions(permissions)
                .active(true)
                .systemRole(shared)
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
        UamRole saved = roleRepository.save(role);
        identityProviderSyncService.syncRole(saved);
        return new CreateOutput(saved);
    }

    public UpdateOutput update(UpdateInput input) {
        String id = input.id();
        UpsertRoleRequest request = input.request();
        UamRole role = requireChangeable(get(new GetInput(id)).role());
        RoleRules.requireEditable(role);
        String code = normalizeCode(request.code());
        RoleRules.requirePlatformAdminForSystemRole(request.systemRole(), TenantGuard.isSuperAdmin());
        RoleRules.requireSameCodeForSystemRole(role, code);
        RoleRules.requireValidCodeIfChanged(role, code);
        roleRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw UamException.conflict("Role already exists");
                });
        List<String> permissions = normalizeCodes(request.permissions());
        // Only what is newly granted has to be held: an admin may still edit a role that carries more than they have.
        Set<String> already = new LinkedHashSet<>(role.getPermissions() == null ? List.of() : role.getPermissions());
        requireCallerHolds(permissions.stream().filter(permission -> !already.contains(permission)).toList());
        role.setCode(code);
        role.setName(defaultName(request.name(), code));
        role.setDescription(normalizeOptional(request.description()));
        role.setPermissions(permissions);
        if (role.getTenantId() == null) {
            // A system role stays one: unflagging it would make it deletable.
            role.setSystemRole(RoleRules.isSystem(role) || request.systemRole());
        }
        if (role.getAudit() != null) {
            role.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        UamRole saved = roleRepository.save(role);
        identityProviderSyncService.syncRole(saved);
        return new UpdateOutput(saved);
    }

    /** Additive: unions the wanted permissions into the role and saves only when something was missing. */
    public AddPermissionsOutput addPermissions(AddPermissionsInput input) {
        String code = normalizeCode(input.code());
        UamRole role = roleRepository.findByCodeIgnoreCase(code).orElseThrow(() -> UamException.notFound("Role not found"));
        Set<String> have = new LinkedHashSet<>(role.getPermissions() == null ? List.of() : role.getPermissions());
        List<String> missing = normalizeCodes(input.permissions()).stream().filter(permission -> !have.contains(permission)).toList();
        if (missing.isEmpty()) {
            return new AddPermissionsOutput(role, List.of());
        }
        have.addAll(missing);
        role.setPermissions(List.copyOf(have));
        if (role.getAudit() != null) {
            role.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        UamRole saved = roleRepository.save(role);
        identityProviderSyncService.syncRole(saved);
        return new AddPermissionsOutput(saved, missing);
    }

    public GetOutput get(GetInput input) {
        UamRole role = roleRepository.findById(input.id()).orElseThrow(() -> UamException.notFound("Role not found"));
        TenantGuard.assertAccessible(role.getTenantId());
        if (RoleVisibility.hiddenFromCaller(role)) {
            throw UamException.notFound("Role not found");
        }
        return new GetOutput(role);
    }

    public ListOutput list(ListInput input) {
        Sort byCode = Sort.by(Sort.Direction.ASC, "code");
        List<UamRole> roles = input.tenantId() == null ? roleRepository.findAll(byCode) : roleRepository.findVisible(input.tenantId(), byCode);
        return new ListOutput(roles.stream().filter(role -> !RoleVisibility.hiddenFromCaller(role)).toList());
    }

    public ListPageOutput list(ListPageInput input) {
        var pageable = ManagementPageSupport.page(input.page(), input.size(), "code");
        // The caller sees the shared roles and the ones of the organization the session works in.
        var found = roleRepository.searchVisible(TenantContext.get(), input.code(), input.name(), pageable);
        var visible = found.getContent().stream().filter(role -> !RoleVisibility.hiddenFromCaller(role)).toList();
        int hidden = found.getNumberOfElements() - visible.size();
        return new ListPageOutput(PageResult.from(hidden == 0 ? found
                : new org.springframework.data.domain.PageImpl<>(visible, pageable, Math.max(0, found.getTotalElements() - hidden))));
    }

    public DeleteOutput delete(DeleteInput input) {
        UamRole role = requireChangeable(get(new GetInput(input.id())).role());
        RoleRules.requireDeletable(role);
        RoleRules.requireUnused(role, userRepository.count(UamUserSearchCriteria.of(role.getCode(), null, null, null)));
        identityProviderSyncService.deleteRole(role);
        roleRepository.delete(role);
        return new DeleteOutput();
    }

    /** A signed-in caller who is not a platform administrator may only grant permissions their own roles carry. */
    private void requireCallerHolds(List<String> wanted) {
        if (wanted.isEmpty() || !TenantGuard.isSignedInUser() || TenantGuard.isSuperAdmin()) {
            return;
        }
        RoleRules.requireHeld(wanted, callerPermissions());
    }

    private Set<String> callerPermissions() {
        return CallerPermissions.held(roleRepository);
    }

    /** A shared role is the platform's: an organization's admin may use it but not change or remove it. */
    private static UamRole requireChangeable(UamRole role) {
        if (role.getTenantId() == null && !TenantGuard.isSuperAdmin()) {
            throw new UamException("Shared roles can only be changed by a platform administrator", HttpStatus.FORBIDDEN);
        }
        return role;
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new UamException("Role code is required");
        }
        return value.trim().toUpperCase();
    }

    private String defaultName(String value, String code) {
        return StringUtils.hasText(value) ? value.trim() : code;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeCodes(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .forEach(normalized::add);
        }
        return List.copyOf(normalized);
    }
}
