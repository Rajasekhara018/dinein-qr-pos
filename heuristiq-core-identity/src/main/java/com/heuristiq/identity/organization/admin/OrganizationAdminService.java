package com.heuristiq.identity.organization.admin;

import com.heuristiq.identity.common.UamCommonConstants;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.IdentityRegisterRequest;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.application.access.AccessSubjectType;
import com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.identity.application.access.GrantApplicationAccessRequest;
import com.heuristiq.identity.bootstrap.PermissionCatalogEntry;
import com.heuristiq.identity.bootstrap.PermissionCatalogLoader;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.notification.NmsNotificationService;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.AddMemberRequest;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;
import com.heuristiq.identity.organization.subscription.repository.TenantApplicationRepository;
import com.heuristiq.identity.registration.UserRegistrationServiceUseCase;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrganizationAdminService implements OrganizationAdminServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.identity.securitypolicy.SecurityPolicyPasswordGenerator policyPasswords;
    private final TenantRepository tenants;
    private final UamRoleRepository roles;
    private final UamUserRepository users;
    private final UserRegistrationServiceUseCase registration;
    private final UserCredentialServiceUseCase credentials;
    private final TenantMembershipServiceUseCase memberships;
    private final IdentityProviderSyncService identitySync;
    private final NmsNotificationService notifications;
    private final PermissionCatalogLoader catalog;
    private final TenantApplicationRepository subscriptions;
    private final ApplicationAccessServiceUseCase access;
    private final String loginUrl;
    private final SecureRandom random = new SecureRandom();

    public OrganizationAdminService(TenantRepository tenants, UamRoleRepository roles, UamUserRepository users,
                                    UserRegistrationServiceUseCase registration, UserCredentialServiceUseCase credentials,
                                    TenantMembershipServiceUseCase memberships, IdentityProviderSyncService identitySync,
                                    NmsNotificationService notifications, PermissionCatalogLoader catalog,
                                    TenantApplicationRepository subscriptions, ApplicationAccessServiceUseCase access,
                                    @org.springframework.beans.factory.annotation.Value("${railcab.uam.organization-admin.login-url:}") String loginUrl) {
        this.tenants = tenants;
        this.roles = roles;
        this.users = users;
        this.registration = registration;
        this.credentials = credentials;
        this.memberships = memberships;
        this.identitySync = identitySync;
        this.notifications = notifications;
        this.catalog = catalog;
        this.subscriptions = subscriptions;
        this.access = access;
        this.loginUrl = loginUrl == null ? "" : loginUrl.trim();
    }

    @Override
    public OrganizationAdminResult provision(ProvisionInput input) {
        Tenant tenant = tenants.findById(input.tenantId())
                .orElseThrow(() -> UamException.notFound("Organization not found"));
        CreateOrganizationAdminRequest request = input.request();
        String mobile = request.mobile().trim();
        String email = request.email().trim();

        UamRole role = adminRole(tenant);
        grantAccessToSubscribedApplications(tenant, role);

        Optional<UamUser> existing = find(mobile, email);
        boolean[] createdHere = {false};
        UamUser user = existing.orElseGet(() -> createOrFindAfterRace(request, mobile, email, createdHere));
        boolean created = existing.isEmpty() && createdHere[0];
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw new UamException("This account is not active, so it cannot administer an organization");
        }

        String temporaryPassword = null;
        if (created) {
            temporaryPassword = policyPasswords == null ? randomPassword() : policyPasswords.generate(user,
                    tenant.getId(), List.of(role.getCode()), List.of());
            credentials.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(user.getId(), temporaryPassword, true));
        }

        memberships.addMember(new TenantMembershipServiceUseCase.AddInput(
                tenant.getId(), new AddMemberRequest(user.getId()), OrganizationAdminConstants.ACTOR));

        Set<String> userRoles = new LinkedHashSet<>(user.getRoles() == null ? List.of() : user.getRoles());
        // A new account only has the placeholder type role it was registered with; the administrator role replaces it.
        if (created) {
            userRoles.clear();
        }
        userRoles.add(role.getCode());
        user.setRoles(new ArrayList<>(userRoles));
        if (user.getTenantId() == null) {
            user.setTenantId(tenant.getId());
        }
        users.save(user);
        notifications.sendOrganizationAdminWelcome(user.getEmail(), phoneOf(user), displayNameOf(user, request), tenant.getCode(), tenant.getId(),
                OrganizationAdminMessages.signInDetails(user.getEmail(), temporaryPassword, loginUrl));
        log.info("Organization administrator set up organization={} userId={} accountCreated={}", tenant.getCode(), user.getId(), created);
        return new OrganizationAdminResult(user.getId(), user.getEmail(), role.getCode(), created, temporaryPassword);
    }

    private static String phoneOf(UamUser user) {
        return user.getPhoneNumber() == null ? null : user.getPhoneNumber().getConcatString();
    }

    private static String displayNameOf(UamUser user, CreateOrganizationAdminRequest request) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank() ? user.getDisplayName() : request.name().trim();
    }

    /**
     * The organization's administrator role: created on first use with every privilege except the platform-only ones, and
     * topped up with any privilege added to the catalog since (never removing one a super admin took away on purpose is not
     * possible to tell apart, so only missing ones are added).
     */
    private UamRole adminRole(Tenant tenant) {
        String code = (tenant.getCode().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_")
                + OrganizationAdminConstants.ADMIN_ROLE_SUFFIX);
        List<String> wanted = OrganizationAdminConstants.administratorPermissions(
                catalog.load().stream().map(PermissionCatalogEntry::code).toList());
        Optional<UamRole> existing = roles.findByCodeIgnoreCase(code);
        if (existing.isPresent()) {
            UamRole role = existing.get();
            if (role.getTenantId() != null && !role.getTenantId().equals(tenant.getId())) {
                throw UamException.conflict("Role " + code + " belongs to another organization");
            }
            Set<String> merged = new LinkedHashSet<>(role.getPermissions() == null ? List.of() : role.getPermissions());
            if (merged.addAll(wanted)) {
                role.setPermissions(new ArrayList<>(merged));
                return roles.save(role);
            }
            return role;
        }
        LocalDateTime now = LocalDateTime.now();
        UamRole saved = roles.save(UamRole.builder()
                .tenantId(tenant.getId())
                .code(code)
                .name(tenant.getName() == null || tenant.getName().isBlank() ? code : tenant.getName() + " administrator")
                .description("Administers the organization's people, roles, applications and structure.")
                .permissions(wanted)
                .active(true)
                .systemRole(false)
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build());
        identitySync.syncRole(saved);
        return saved;
    }

    @Override
    public boolean refreshRole(Tenant tenant) {
        String code = tenant.getCode().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_")
                + OrganizationAdminConstants.ADMIN_ROLE_SUFFIX;
        if (roles.findByCodeIgnoreCase(code).filter(r -> tenant.getId().equals(r.getTenantId())).isEmpty()) {
            return false;
        }
        grantAccessToSubscribedApplications(tenant, adminRole(tenant));
        return true;
    }

    /** The administrator can sign in to every application the organization has (and, later, each one it subscribes to). */
    private void grantAccessToSubscribedApplications(Tenant tenant, UamRole role) {
        subscriptions.findByTenantId(tenant.getId()).stream()
                .filter(s -> s.getStatus() == TenantApplicationStatus.ACTIVE)
                .forEach(s -> grantAccess(role.getCode(), s.getApplicationCode()));
    }

    /** Gives the administrator role access to one application; safe to repeat. */
    @Override
    public void grantAccess(String roleCode, String applicationCode) {
        access.grant(new ApplicationAccessServiceUseCase.GrantInput(
                new GrantApplicationAccessRequest(applicationCode, AccessSubjectType.ROLE, roleCode,
                        OrganizationAdminConstants.ACCESS_NOTE),
                OrganizationAdminConstants.ACTOR));
    }

    private UamUser createOrFindAfterRace(CreateOrganizationAdminRequest request, String mobile, String email, boolean[] createdHere) {
        try {
            String userId = registration.register(new UserRegistrationServiceUseCase.RegisterInput(
                    new IdentityRegisterRequest(request.name().trim(), mobile, email, null, UamEnums.UserType.OPS), true)).response().userId();
            createdHere[0] = true;
            return users.findById(userId)
                    .orElseThrow(() -> new UamException("Account was created but could not be read back", HttpStatus.INTERNAL_SERVER_ERROR));
        } catch (UamException e) {
            if (e.getStatus() == HttpStatus.CONFLICT) {
                return find(mobile, email).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private Optional<UamUser> find(String mobile, String email) {
        Optional<UamUser> byPhone = users.findByPhone(mobile);
        Optional<UamUser> byEmail = users.findByEmailIgnoreCase(email);
        if (byPhone.isPresent() && byEmail.isPresent() && !byPhone.get().getId().equals(byEmail.get().getId())) {
            throw UamException.conflict("This mobile number and email belong to different accounts");
        }
        return byPhone.isPresent() ? byPhone : byEmail;
    }

    /** One character from each class, then random, then shuffled; look-alike characters are left out. */
    private String randomPassword() {
        String all = UamCommonConstants.PASSWORD_UPPER + UamCommonConstants.PASSWORD_LOWER + UamCommonConstants.PASSWORD_DIGITS + UamCommonConstants.PASSWORD_MESSAGE_SAFE_SYMBOLS;
        StringBuilder out = new StringBuilder(OrganizationAdminConstants.TEMPORARY_PASSWORD_LENGTH);
        for (String pool : new String[] {UamCommonConstants.PASSWORD_UPPER, UamCommonConstants.PASSWORD_LOWER, UamCommonConstants.PASSWORD_DIGITS, UamCommonConstants.PASSWORD_MESSAGE_SAFE_SYMBOLS}) {
            out.append(pool.charAt(random.nextInt(pool.length())));
        }
        while (out.length() < OrganizationAdminConstants.TEMPORARY_PASSWORD_LENGTH) {
            out.append(all.charAt(random.nextInt(all.length())));
        }
        char[] chars = out.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = chars[i];
            chars[i] = chars[j];
            chars[j] = swap;
        }
        return new String(chars);
    }
}
