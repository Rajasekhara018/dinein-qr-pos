package com.heuristiq.uam.bootstrap;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.organization.PlatformOrganizationProperties;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.users.NewUamUserBuilder;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import lombok.extern.slf4j.Slf4j;
import com.heuristiq.uam.helper.RPhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class UamBootstrapRunner implements CommandLineRunner {
    private final UamRoleRepository roleRepository;
    private final PermissionCatalogProvisioner catalogProvisioner;
    private final UamUserRepository userRepository;
    private final UserCredentialServiceUseCase userCredentialService;
    private List<PermissionCatalogEntry> provisionedEntries = List.of();
    private final String adminUsername;
    private final String adminPassword;
    private final String adminEmail;
    private final String adminPhone;
    private final TenantRepository tenantRepository;
    private final PlatformOrganizationProperties platformOrganization;
    private final TenantMembershipServiceUseCase memberships;

    public UamBootstrapRunner(UamRoleRepository roleRepository,
                              PermissionCatalogProvisioner catalogProvisioner,
                              UamUserRepository userRepository,
                              UserCredentialServiceUseCase userCredentialService,
                              @Value("${railcab.uam.bootstrap.admin-username:}") String adminUsername,
                              @Value("${railcab.uam.bootstrap.admin-password:}") String adminPassword,
                              @Value("${railcab.uam.bootstrap.admin-email:}") String adminEmail,
                              @Value("${railcab.uam.bootstrap.admin-phone:}") String adminPhone,
                              TenantRepository tenantRepository,
                              PlatformOrganizationProperties platformOrganization,
                              TenantMembershipServiceUseCase memberships) {
        this.roleRepository = roleRepository;
        this.catalogProvisioner = catalogProvisioner;
        this.userRepository = userRepository;
        this.userCredentialService = userCredentialService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminEmail = adminEmail;
        this.adminPhone = adminPhone;
        this.tenantRepository = tenantRepository;
        this.platformOrganization = platformOrganization;
        this.memberships = memberships;
    }

    @Override
    public void run(String... args) {
        provisionedEntries = catalogProvisioner.bootstrap();
        seedRoles();
        seedBootstrapAdmin();
    }

    private void seedRoles() {
        LocalDateTime now = LocalDateTime.now();
        List<String> allPermissionCodes = provisionedEntries.stream()
                .map(PermissionCatalogEntry::code)
                .toList();
        seedOrSyncSuperAdminRole(allPermissionCodes, now);

        Set<String> knownCodes = new HashSet<>(allPermissionCodes);
        BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS.forEach((code, permissions) -> {
            seedOrMergeStructuralRole(code, permissions.stream().filter(knownCodes::contains).toList(), now);
        });
    }

    // Not re-synced every boot (unlike SUPER_ADMIN) so a SUPER_ADMIN can freely adjust
    // CUSTOMER/DRIVER's permission set afterwards via RoleController without the boot process
    // reverting it. Only when BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION is bumped does an existing role
    // get the default codes merged in -- additively, once, keeping any codes an admin added.
    private void seedOrMergeStructuralRole(String code, List<String> permissions, LocalDateTime now) {
        Optional<UamRole> existing = roleRepository.findByCodeIgnoreCase(code);
        if (existing.isPresent()) {
            mergeDefaultPermissionsIfOutdated(existing.get(), permissions, now);
            return;
        }
        roleRepository.save(UamRole.builder()
                .code(code)
                .name(code)
                .description("RealBoom " + code + " role")
                .permissions(permissions)
                .active(true)
                .systemRole(true)
                .editable(true)
                .attributes(new HashMap<>(Map.of(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE, BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION)))
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build());
    }

    private void mergeDefaultPermissionsIfOutdated(UamRole role, List<String> defaults, LocalDateTime now) {
        Map<String, Object> attributes = role.getAttributes() == null
                ? new HashMap<>()
                : new HashMap<>(role.getAttributes());
        Object storedVersion = attributes.get(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE);
        int appliedVersion = storedVersion instanceof Number n ? n.intValue() : 1;
        if (appliedVersion >= BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION) {
            return;
        }

        Set<String> merged = new LinkedHashSet<>(role.getPermissions() == null ? List.of() : role.getPermissions());
        int before = merged.size();
        merged.addAll(defaults);
        merged.removeAll(BootstrapRoleConstants.STRUCTURAL_ROLE_REVOKED_PERMISSIONS.getOrDefault(role.getCode(), List.of()));
        // v12 retires every monolith permission, including any an administrator added by hand.
        merged.removeIf(permission -> permission != null && permission.startsWith("BOOKINGS_"));
        role.setPermissions(new ArrayList<>(merged));

        attributes.put(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE, BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION);
        role.setAttributes(attributes);
        if (role.getAudit() != null) {
            role.getAudit().setUpdatedAt(now);
        }
        roleRepository.save(role);
        log.info("Merged {} default permission(s) into existing {} role (defaults v{} -> v{})",
                merged.size() - before, role.getCode(), appliedVersion, BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION);
    }

    private void seedOrSyncSuperAdminRole(List<String> allPermissionCodes, LocalDateTime now) {
        Optional<UamRole> existing = roleRepository.findByCodeIgnoreCase(UamCommonConstants.SUPER_ADMIN_ROLE);
        if (existing.isPresent()) {
            UamRole role = existing.get();
            role.setPermissions(allPermissionCodes);
            if (role.getAudit() != null) {
                role.getAudit().setUpdatedAt(now);
            }
            roleRepository.save(role);
            return;
        }
        roleRepository.save(UamRole.builder()
                .code(UamCommonConstants.SUPER_ADMIN_ROLE)
                .name(UamCommonConstants.SUPER_ADMIN_ROLE)
                .description("RealBoom SUPER_ADMIN role")
                .permissions(allPermissionCodes)
                .active(true)
                .systemRole(true)
                .editable(false)
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build());
    }

    private void seedBootstrapAdmin() {
        if (!StringUtils.hasText(adminUsername) || !StringUtils.hasText(adminPassword)) {
            return;
        }
        Optional<UamUser> existing = userRepository.findByUserNameIgnoreCase(adminUsername.trim());
        if (existing.isPresent()) {
            syncExistingAdmin(existing.get());
            return;
        }
        // Also guard against a pre-existing user with the same email under a *different* username
        // (e.g. from earlier manual testing) -- email has a unique index, so without this check the
        // insert below throws DuplicateKeyException and crashes the whole application at startup.
        if (StringUtils.hasText(adminEmail) && userRepository.findByEmailIgnoreCase(adminEmail.trim()).isPresent()) {
            log.warn("Skipping bootstrap admin seed: a user with email {} already exists under a different username", adminEmail.trim());
            return;
        }
        UamUser admin = userRepository.save(NewUamUserBuilder.newUser()
                .userName(adminUsername.trim())
                .email(StringUtils.hasText(adminEmail) ? adminEmail.trim() : null)
                .fullName(new RName(BootstrapAdminConstants.ADMIN_FULL_NAME))
                // phoneNumber is @NotNull on UamUserEntity (enforced on JPA persist, never by Mongo),
                // so the postgres profile needs at least an empty embedded value here.
                .phoneNumber(StringUtils.hasText(adminPhone) ? new RPhoneNumber(adminPhone.trim()) : new RPhoneNumber())
                .roles(List.of(UamCommonConstants.SUPER_ADMIN_ROLE))
                // DefaultOrganizationBootstrap is @Order(100) and this runner has no order (lowest precedence), so the platform
                // organization already exists here and the new admin is placed in it at once. Its membership is created below,
                // because that bootstrap's membership backfill ran before this account existed.
                .tenantId(tenantRepository.findByCodeIgnoreCase(platformOrganization.code()).map(Tenant::getId).orElse(null))
                .build());
        userCredentialService.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(admin.getId(), adminPassword, false));
        giveAdminItsMembership(admin);
    }

    /**
     * Creates the new admin's membership of the platform organization in this same start, so it can sign in after a fresh seed without a
     * second restart. Idempotent (an account that already belongs somewhere is left as it is); a failure only logs, since a start-up step
     * must never stop UAM, and the next start's backfill still covers it.
     */
    private void giveAdminItsMembership(UamUser admin) {
        try {
            memberships.ensureAnyMembership(admin);
        } catch (RuntimeException e) {
            log.warn("Could not create the bootstrap super admin's membership: {}", e.getMessage());
        }
    }

    /**
     * Brings the bootstrap super admin that already exists into line with the current settings, on every start: the RealBoom display name,
     * the configured phone number (when {@code railcab.uam.bootstrap.admin-phone} is set; a user record needs one) and the SUPER_ADMIN role.
     * The login name, email and password are never touched. Nothing is saved when nothing differs, and a failure only logs a warning: this
     * must never stop UAM from starting.
     */
    void syncExistingAdmin(UamUser admin) {
        boolean changed = false;
        RName name = admin.getFullName();
        if (name == null || !BootstrapAdminConstants.ADMIN_FULL_NAME.equals(name.getFullName().trim())) {
            admin.setFullName(new RName(BootstrapAdminConstants.ADMIN_FULL_NAME));
            changed = true;
        }
        if (StringUtils.hasText(adminPhone)) {
            RPhoneNumber wanted = new RPhoneNumber(adminPhone.trim());
            if (admin.getPhoneNumber() == null || !wanted.equals(admin.getPhoneNumber())) {
                admin.setPhoneNumber(wanted);
                changed = true;
            }
        }
        List<String> roles = admin.getRoles() == null ? new ArrayList<>() : new ArrayList<>(admin.getRoles());
        if (roles.stream().noneMatch(UamCommonConstants.SUPER_ADMIN_ROLE::equalsIgnoreCase)) {
            roles.add(UamCommonConstants.SUPER_ADMIN_ROLE);
            admin.setRoles(roles);
            changed = true;
        }
        if (!changed) {
            return;
        }
        if (admin.getPhoneNumber() == null || !admin.getPhoneNumber().hasValue()) {
            log.info("The bootstrap super admin has no phone number and railcab.uam.bootstrap.admin-phone is not set: it cannot be updated");
            return;
        }
        try {
            userRepository.save(admin);
            log.info("Updated the bootstrap super admin ({})", BootstrapAdminConstants.ADMIN_FULL_NAME);
        } catch (RuntimeException e) {
            log.warn("Could not update the bootstrap super admin: {}", e.getMessage());
        }
    }
}
