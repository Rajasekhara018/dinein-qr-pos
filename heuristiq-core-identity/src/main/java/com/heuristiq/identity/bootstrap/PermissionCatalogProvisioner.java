package com.heuristiq.identity.bootstrap;

import com.heuristiq.identity.application.Application;
import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Imports catalog definitions globally once per product; organization/user access is granted separately. */
@Service
public class PermissionCatalogProvisioner {
    public static final String IMPORT_STATUS = "privilegeCatalogStatus";
    public static final String IMPORT_PREFIX = "privilegeCatalogPrefix";
    private final PermissionCatalogLoader loader;
    private final PermissionRepository permissions;
    private final ApplicationRepository applications;
    private final UamRoleRepository roles;

    public PermissionCatalogProvisioner(PermissionCatalogLoader loader, PermissionRepository permissions,
                                        ApplicationRepository applications, UamRoleRepository roles) {
        this.loader = loader;
        this.permissions = permissions;
        this.applications = applications;
        this.roles = roles;
    }

    public List<PermissionCatalogEntry> bootstrap() {
        List<PermissionCatalogEntry> shared = loader.loadShared();
        upsert(shared, "UAM");
        Map<String, PermissionCatalogEntry> installed = new LinkedHashMap<>();
        shared.forEach(entry -> installed.put(entry.code(), entry));
        for (Application application : applications.findAll(Sort.by("code"))) {
            List<PermissionCatalogEntry> entries = importApplication(application, false);
            entries.forEach(entry -> installed.put(entry.code(), entry));
        }
        return List.copyOf(installed.values());
    }

    public void provisionApplication(Application application) {
        importApplication(application, true);
    }

    private List<PermissionCatalogEntry> importApplication(Application application, boolean syncSuperAdmin) {
        String prefix = ApplicationCatalogue.find(application.getCode()).map(entry -> entry.privilegePrefix()).orElse(null);
        if (prefix == null) return List.of(); // An application without a privilege prefix has no business permission definitions.
        List<PermissionCatalogEntry> entries = loader.loadApplication(application.getCode());
        if (entries.isEmpty()) throw new IllegalStateException("No privilege definitions for application " + application.getCode());
        Map<String, Object> attributes = application.getAttributes() == null ? new HashMap<>() : new HashMap<>(application.getAttributes());
        boolean firstImport = !"COMPLETE".equals(attributes.get(IMPORT_STATUS)) || !prefix.equals(attributes.get(IMPORT_PREFIX));
        if (firstImport) {
            attributes.put(IMPORT_STATUS, "PENDING");
            attributes.put(IMPORT_PREFIX, prefix);
            application.setAttributes(attributes);
            applications.save(application);
        }
        upsert(entries, application.getCode());
        if (firstImport) grantStructuralDefaults(entries);
        if (syncSuperAdmin) synchronizeSuperAdmin();
        if (firstImport || !Integer.valueOf(UamCommonConstants.PERMISSION_CATALOG_VERSION).equals(attributes.get("privilegeCatalogVersion"))) {
            attributes.put(IMPORT_STATUS, "COMPLETE");
            attributes.put("privilegeCatalogVersion", UamCommonConstants.PERMISSION_CATALOG_VERSION);
            application.setAttributes(attributes);
            applications.save(application);
        }
        return entries;
    }

    private void upsert(List<PermissionCatalogEntry> entries, String applicationCode) {
        LocalDateTime now = LocalDateTime.now();
        for (PermissionCatalogEntry entry : entries) {
            Permission permission = permissions.findByCodeIgnoreCase(entry.code()).orElseGet(() -> Permission.builder()
                    .code(entry.code()).active(true).systemPermission(true)
                    .audit(AuditInfo.builder().createdAt(now).build()).build());
            permission.setApplicationCode(applicationCode);
            permission.setModule(entry.module());
            permission.setResource(entry.resource());
            permission.setAction(entry.action());
            permission.setDisplayName(entry.displayName());
            permission.setDescription(entry.description());
            Map<String, Object> attributes = permission.getAttributes() == null ? new HashMap<>() : new HashMap<>(permission.getAttributes());
            attributes.put(UamCommonConstants.PERMISSION_PRIVILEGE_ID_ATTRIBUTE, entry.privilegeId());
            attributes.put(UamCommonConstants.PERMISSION_CATALOG_VERSION_ATTRIBUTE, UamCommonConstants.PERMISSION_CATALOG_VERSION);
            attributes.put(UamCommonConstants.PERMISSION_API_PATH_ATTRIBUTE, entry.apipath());
            if (entry.privilegeCode() != null) attributes.put("privilegeCode", entry.privilegeCode());
            if (entry.service() != null) attributes.put("service", entry.service());
            permission.setAttributes(attributes);
            if (permission.getAudit() != null) permission.getAudit().setUpdatedAt(now);
            permissions.save(permission);
        }
    }

    private void grantStructuralDefaults(List<PermissionCatalogEntry> entries) {
        Set<String> imported = entries.stream().map(PermissionCatalogEntry::code).collect(Collectors.toSet());
        BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS.forEach((code, defaults) -> roles.findByCodeIgnoreCase(code).ifPresent(role -> {
            Set<String> merged = new LinkedHashSet<>(role.getPermissions() == null ? List.of() : role.getPermissions());
            boolean changed = merged.addAll(defaults.stream().filter(imported::contains).toList());
            if (changed) {
                role.setPermissions(new ArrayList<>(merged));
                if (role.getAudit() != null) role.getAudit().setUpdatedAt(LocalDateTime.now());
                roles.save(role);
            }
        }));
    }

    private void synchronizeSuperAdmin() {
        roles.findByCodeIgnoreCase(UamCommonConstants.SUPER_ADMIN_ROLE).ifPresent(role -> {
            Set<String> available = new LinkedHashSet<>();
            loader.loadShared().forEach(entry -> available.add(entry.code()));
            applications.findAll(Sort.by("code")).forEach(app -> loader.loadApplication(app.getCode())
                    .forEach(entry -> available.add(entry.code())));
            role.setPermissions(new ArrayList<>(available));
            if (role.getAudit() != null) role.getAudit().setUpdatedAt(LocalDateTime.now());
            roles.save(role);
        });
    }
}
