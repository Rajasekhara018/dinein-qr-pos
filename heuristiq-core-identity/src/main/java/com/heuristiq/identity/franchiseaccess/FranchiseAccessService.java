package com.heuristiq.uam.franchiseaccess;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.uam.application.access.AccessSubjectType;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.application.access.GrantApplicationAccessRequest;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertRoleRequest;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.ApplicationResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.SubscribedResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.UserResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.UsersResponse;
import com.heuristiq.uam.users.UamUserConstants;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.membership.AddMemberRequest;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.orgstructure.domain.LoginAccounts;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.role.UamRoleServiceUseCase;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.UamUserServiceUseCase;

/**
 * What Rider_FranchiseService needs from the platform: applications, accounts, organization membership, roles, subscriptions
 * and application grants. Each call is a thin step over the feature's own use case, so every account, role and tenancy rule
 * stays where it lives. Creating a login runs in the named organization's context, since the caller is a service with no
 * organization of its own.
 */
@Service
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class FranchiseAccessService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FranchiseAccessService.class);

    private final ApplicationRepository applications;
    private final UamUserServiceUseCase users;
    private final TenantMembershipServiceUseCase memberships;
    private final TenantApplicationServiceUseCase subscriptions;
    private final ApplicationAccessServiceUseCase accessGrants;
    private final UamRoleRepository roleRepository;
    private final UamRoleServiceUseCase roles;
    private final TenantRepository tenants;
    private final LoginAccounts logins;
    private final FranchiseAccessProperties limits;

    public FranchiseAccessService(ApplicationRepository applications, UamUserServiceUseCase users,
                                  TenantMembershipServiceUseCase memberships, TenantApplicationServiceUseCase subscriptions,
                                  ApplicationAccessServiceUseCase accessGrants, UamRoleRepository roleRepository,
                                  UamRoleServiceUseCase roles, TenantRepository tenants, LoginAccounts logins) {
        this(applications, users, memberships, subscriptions, accessGrants, roleRepository, roles, tenants, logins,
                FranchiseAccessProperties.defaults());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FranchiseAccessService(ApplicationRepository applications, UamUserServiceUseCase users,
                                  TenantMembershipServiceUseCase memberships, TenantApplicationServiceUseCase subscriptions,
                                  ApplicationAccessServiceUseCase accessGrants, UamRoleRepository roleRepository,
                                  UamRoleServiceUseCase roles, TenantRepository tenants, LoginAccounts logins,
                                  FranchiseAccessProperties limits) {
        this.limits = limits;
        this.applications = applications;
        this.users = users;
        this.memberships = memberships;
        this.subscriptions = subscriptions;
        this.accessGrants = accessGrants;
        this.roleRepository = roleRepository;
        this.roles = roles;
        this.tenants = tenants;
        this.logins = logins;
    }

    public boolean applicationExists(String code) {
        return StringUtils.hasText(code) && applications.existsByCodeIgnoreCase(code.trim());
    }
    /**
     * The codes of every active application, upper-cased and sorted. An application switched off is left out: nothing signs in to
     * it, so it needs no franchise roles yet; it appears here again when it is switched on.
     */
    public List<String> applicationCodes() {
        return applications.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .filter(app -> !Boolean.FALSE.equals(app.getActive()))
                .map(app -> app.getCode())
                .filter(StringUtils::hasText)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    /** The application with this code (any case), or {@link ApplicationResponse#missing()}. */
    public ApplicationResponse application(String code) {
        if (!StringUtils.hasText(code)) {
            return ApplicationResponse.missing();
        }
        return applications.findByCodeIgnoreCase(code.trim())
                .map(app -> new ApplicationResponse(true, app.getCode(), app.getName(), app.getLandingPage(), app.getBaseUrl()))
                .orElseGet(ApplicationResponse::missing);
    }

    /**
     * The account with this email (as email or username) that is an ACTIVE member of the organization;
     * {@link UserResponse#missing()} when there is none, so the API cannot be used to enumerate other organizations' people.
     */
    public UserResponse userByEmail(String tenantId, String email) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(tenantId)) {
            return UserResponse.missing();
        }
        String wanted = email.trim();
        return users.search(new UamUserServiceUseCase.SearchInput(0, 10, null, null, wanted, null)).users().content().stream()
                .filter(user -> wanted.equalsIgnoreCase(user.getEmail()) || wanted.equalsIgnoreCase(user.getUserName()))
                .filter(user -> isActiveMember(tenantId, user.getId()))
                .findFirst().map(FranchiseAccessService::view).orElseGet(UserResponse::missing);
    }

    /** The account when it is an ACTIVE member of the organization, otherwise {@link UserResponse#missing()}. */
    public UserResponse userById(String tenantId, String userId) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            return UserResponse.missing();
        }
        try {
            UamUser user = users.get(new UamUserServiceUseCase.GetInput(userId)).user();
            return isActiveMember(tenantId, user.getId()) ? view(user) : UserResponse.missing();
        } catch (UamException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return UserResponse.missing();
            }
            throw e;
        }
    }

    /**
     * Several accounts at once, so a screen that shows many owners makes one call. Only active members of the organization are
     * returned (an unknown id, or an account of another organization, is simply absent). At most
     * {@link UamUserConstants#MAX_BATCH_LOOKUP} distinct ids are looked up.
     */
    public UsersResponse usersByIds(String tenantId, Collection<String> userIds) {
        if (!StringUtils.hasText(tenantId) || userIds == null) {
            return new UsersResponse(List.of());
        }
        List<String> ids = userIds.stream().filter(StringUtils::hasText).map(String::trim).distinct()
                .limit(UamUserConstants.MAX_BATCH_LOOKUP).toList();
        if (ids.isEmpty()) {
            return new UsersResponse(List.of());
        }
        Set<String> members = memberships.activeMembersAmong(tenantId, ids);
        return new UsersResponse(users.findByIds(new UamUserServiceUseCase.FindByIdsInput(ids)).users().stream()
                .filter(user -> members.contains(user.getId())).map(FranchiseAccessService::view).toList());
    }

    public boolean isActiveMember(String tenantId, String userId) {
        return memberships.activeMembersAmong(tenantId, List.of(userId)).contains(userId);
    }

    public void addMember(String tenantId, String userId, String actor) {
        memberships.addMember(new TenantMembershipServiceUseCase.AddInput(tenantId, new AddMemberRequest(userId), actorOrDefault(actor)));
    }

    /** The organization's support email, or empty when it has none or does not exist. */
    public Optional<String> supportEmail(String tenantId) {
        return tenants.findById(tenantId).map(Tenant::getSupportEmail).filter(StringUtils::hasText);
    }

    public Set<String> roleCodes(String tenantId) {
        return logins.roleCodes(tenantId);
    }

    public boolean emailInUse(String email) {
        return StringUtils.hasText(email) && logins.emailInUse(email.trim());
    }

    /**
     * Makes a fixed franchise role (owner or agent of an application) exist with at least the given privileges: creates it when
     * missing, and when it exists ADDS whichever of the privileges it lacks. It never removes a privilege and never changes the
     * name, description, active or system flag, so what an administrator added stays and a role an older release created empty is
     * repaired. Idempotent, and safe when two callers ensure the same role at once (losing the create race falls through to the
     * additive step, which converges to the same set). Refuses an administrator role code, any privilege outside the configured
     * allow-list, and any role (new or existing) that is not a fixed franchise role. Fails loudly (500) if the role still lacks a requested
     * privilege afterwards. The role is never marked as a system role (the role service would only honour that for a super admin).
     */
    public void ensureRole(String roleCode, String name, String description, Set<String> privileges) {
        if (!StringUtils.hasText(roleCode)) {
            throw new UamException("roleCode is required");
        }
        requireNotDenied(roleCode);
        requireFixedFranchiseRole(roleCode);
        Set<String> wanted = withoutRetired(roleCode, privileges);
        requirePrivilegesAllowed(wanted);
        if (roleRepository.findByCodeIgnoreCase(roleCode.trim()).isEmpty()) {
            try {
                roles.create(new UamRoleServiceUseCase.CreateInput(new UpsertRoleRequest(roleCode, name, description, wanted, false)));
            } catch (UamException e) {
                if (e.getStatus() != HttpStatus.CONFLICT) {
                    throw e;
                }
            }
        }
        UamRole existing = roleRepository.findByCodeIgnoreCase(roleCode.trim())
                .orElseThrow(() -> new UamException("Role was not created: " + roleCode.trim(), HttpStatus.INTERNAL_SERVER_ERROR));
        if (!lacking(existing, wanted).isEmpty()) {
            var added = roles.addPermissions(new UamRoleServiceUseCase.AddPermissionsInput(existing.getCode(), wanted));
            log.info("Franchise role {} was missing {} privilege(s), added: {}", existing.getCode(), added.added().size(), added.added());
        }
        UamRole after = roleRepository.findByCodeIgnoreCase(roleCode.trim()).orElse(existing);
        List<String> stillLacking = lacking(after, wanted);
        if (!stillLacking.isEmpty()) {
            throw new UamException("Role " + after.getCode() + " still lacks privileges: " + stillLacking, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** The privileges without the retired ones (logged once per call, never a failure); anything else unknown is still refused later. */
    private static Set<String> withoutRetired(String roleCode, Set<String> privileges) {
        if (privileges == null) {
            return Set.of();
        }
        Set<String> retired = privileges.stream().filter(p -> p != null
                && FranchiseAccessConstants.RETIRED_PRIVILEGES.contains(p.trim().toUpperCase(Locale.ROOT))).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        if (!retired.isEmpty()) {
            log.warn("Ignoring retired privilege(s) {} sent for franchise role {} (an older franchise service build?)", retired, roleCode);
        }
        return privileges.stream().filter(p -> !retired.contains(p)).collect(java.util.stream.Collectors.toSet());
    }

    /** The wanted privileges (upper case) the role does not have yet. */
    private static List<String> lacking(UamRole role, Set<String> wanted) {
        Set<String> have = role.getPermissions() == null ? Set.of()
                : role.getPermissions().stream().map(permission -> permission.trim().toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        return wanted.stream().filter(StringUtils::hasText).map(permission -> permission.trim().toUpperCase(Locale.ROOT))
                .filter(permission -> !have.contains(permission)).sorted().toList();
    }

    /** Only the owner and agent roles of the franchise service (a configured base, or a base followed by an application code) may be extended. */
    private void requireFixedFranchiseRole(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        boolean fixed = limits.fixedRoleBases().stream().filter(StringUtils::hasText).map(base -> base.trim().toUpperCase(Locale.ROOT))
                .anyMatch(base -> normalized.equals(base) || (normalized.startsWith(base + FranchiseAccessConstants.ROLE_CODE_SEPARATOR)
                        && normalized.length() > base.length() + FranchiseAccessConstants.ROLE_CODE_SEPARATOR.length()));
        if (!fixed) {
            throw new UamException("Not a franchise owner or agent role, so it is not changed here: " + normalized, HttpStatus.FORBIDDEN);
        }
    }

    /** Gives the account these roles in addition to its own. */
    public void addRoles(String userId, List<String> roleCodes) {
        List<String> wanted = roleCodes == null ? List.of() : roleCodes;
        if (!wanted.isEmpty()) {
            UamUser user = users.get(new UamUserServiceUseCase.GetInput(userId)).user();
            wanted.stream().filter(StringUtils::hasText).forEach(this::requireNotDenied);
            wanted.stream().filter(StringUtils::hasText).forEach(code -> requireGrantable(user, code));
        }
        users.addRoles(new UamUserServiceUseCase.AddRolesInput(userId, wanted));
    }

    /** A role may be given when it is not an administrator role, exists, and is shared or belongs to an organization of the user. */
    private void requireGrantable(UamUser user, String code) {
        requireNotDenied(code);
        UamRole role = roleRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new UamException("Role does not exist: " + code.trim(), HttpStatus.BAD_REQUEST));
        String owner = role.getTenantId();
        if (owner != null && !owner.equals(user.getTenantId()) && !isActiveMember(owner, user.getId())) {
            throw new UamException("Role belongs to another organization: " + code.trim(), HttpStatus.FORBIDDEN);
        }
    }

    private void requireNotDenied(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        boolean denied = normalized.endsWith(FranchiseAccessConstants.ADMIN_ROLE_SUFFIX)
                || limits.deniedRoles().stream().anyMatch(normalized::equalsIgnoreCase)
                || FranchiseAccessConstants.DEFAULT_DENIED_ROLES.contains(normalized);
        if (denied) {
            throw new UamException("Role cannot be managed through the franchise service: " + normalized, HttpStatus.FORBIDDEN);
        }
    }

    private void requirePrivilegesAllowed(Set<String> privileges) {
        if (privileges == null) {
            return;
        }
        for (String privilege : privileges) {
            String normalized = privilege == null ? "" : privilege.trim().toUpperCase(Locale.ROOT);
            if (limits.allowedPrivilegePrefixes().stream().noneMatch(allowed -> !allowed.isBlank()
                    && normalized.equals(allowed.trim().toUpperCase(Locale.ROOT)))) {
                throw new UamException("Privilege is not allowed for a franchise role: " + privilege, HttpStatus.FORBIDDEN);
            }
        }
    }

    /**
     * Whether the organization uses the application today: an ACTIVE subscription that has not passed its end date, and only this
     * organization's own (another organization's link never counts). Read only: it never creates or changes a subscription.
     */
    public SubscribedResponse subscription(String tenantId, String applicationCode) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(applicationCode)) {
            return new SubscribedResponse(false, null, null);
        }
        String wanted = applicationCode.trim();
        boolean subscribed = subscriptions.activeApplicationCodes(tenantId.trim()).stream().anyMatch(wanted::equalsIgnoreCase);
        String organization = tenants.findById(tenantId.trim()).map(Tenant::getName).filter(StringUtils::hasText).orElse(null);
        String application = applications.findByCodeIgnoreCase(wanted).map(app -> app.getName()).filter(StringUtils::hasText).orElse(null);
        return new SubscribedResponse(subscribed, organization, application);
    }

    /**
     * The applications the organization uses today (ACTIVE subscriptions not past their end date, only its own), upper case,
     * sorted, without repeats. Read only. Empty for a blank organization.
     */
    public List<String> subscribedApplicationCodes(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return List.of();
        }
        return subscriptions.activeApplicationCodes(tenantId.trim()).stream().filter(StringUtils::hasText)
                .map(code -> code.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
    }

    /**
     * Whether the account is an active member of the organization and holds the privilege (exact name in privilege.json) through one of
     * its roles. Roles of another organization do not count; shared roles (no organization) and the organization's own do. Decided from
     * privileges only, never from role names. Read only.
     */
    public boolean holdsPrivilege(String tenantId, String userId, String privilege) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId) || !StringUtils.hasText(privilege)
                || !isActiveMember(tenantId.trim(), userId.trim())) {
            return false;
        }
        UamUser user;
        try {
            user = users.get(new UamUserServiceUseCase.GetInput(userId.trim())).user();
        } catch (UamException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw e;
        }
        if (user.getRoles() == null) {
            return false;
        }
        String wanted = privilege.trim().toUpperCase(Locale.ROOT);
        return user.getRoles().stream().filter(StringUtils::hasText)
                .map(code -> roleRepository.findByCodeIgnoreCase(code.trim()))
                .flatMap(Optional::stream)
                .filter(role -> role.getTenantId() == null || role.getTenantId().equals(tenantId.trim()))
                .anyMatch(role -> role.getPermissions() != null && role.getPermissions().stream()
                        .anyMatch(permission -> permission != null && wanted.equals(permission.trim().toUpperCase(Locale.ROOT))));
    }

    public void subscribe(String tenantId, String applicationCode, String actor) {
        subscriptions.ensureSubscribed(tenantId, applicationCode, actorOrDefault(actor));
    }

    public void grant(String userId, String applicationCode, String actor, String note) {
        accessGrants.grant(new ApplicationAccessServiceUseCase.GrantInput(new GrantApplicationAccessRequest(applicationCode,
                AccessSubjectType.USER, userId, StringUtils.hasText(note) ? note : FranchiseAccessConstants.DEFAULT_GRANT_NOTE),
                actorOrDefault(actor)));
    }

    /** Creates a staff login in the organization, running in that organization's context so the account is owned by it. */
    public String createLogin(String tenantId, String email, String name, String phone, List<String> roleCodes) {
        if (!StringUtils.hasText(email)) {
            throw new UamException("email is required");
        }
        if (roleCodes != null) {
            roleCodes.stream().filter(StringUtils::hasText).forEach(this::requireNotDenied);
        }
        return inTenant(tenantId, () -> logins.createLogin(tenantId, email.trim(), name, phone, roleCodes == null ? List.of() : roleCodes));
    }

    /**
     * Removes a login the franchise service created a moment ago and could not finish setting up. Idempotent (an unknown
     * account is already gone). Only an account owned by this organization is removed, and never one holding an
     * administrator or otherwise denied role, so the call cannot be used to delete real staff.
     */
    public void deleteLogin(String tenantId, String userId) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            throw new UamException("tenantId and userId are required");
        }
        UamUser user;
        try {
            user = users.get(new UamUserServiceUseCase.GetInput(userId)).user();
        } catch (UamException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw e;
        }
        if (!tenantId.equals(user.getTenantId())) {
            throw new UamException("Login does not belong to this organization", HttpStatus.FORBIDDEN);
        }
        if (user.getRoles() != null) {
            user.getRoles().stream().filter(StringUtils::hasText).forEach(this::requireNotDenied);
        }
        inTenant(tenantId, () -> users.delete(new UamUserServiceUseCase.DeleteInput(userId)));
    }

    private static <T> T inTenant(String tenantId, Supplier<T> work) {
        String previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private static String actorOrDefault(String actor) {
        return StringUtils.hasText(actor) ? actor : FranchiseAccessConstants.DEFAULT_ACTOR;
    }

    private static UserResponse view(UamUser user) {
        List<String> roleList = user.getRoles() == null ? List.of()
                : user.getRoles().stream().map(role -> role.toUpperCase(Locale.ROOT)).toList();
        String display = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : null;
        return new UserResponse(true, user.getId(), display, user.getEmail(), roleList);
    }
}
