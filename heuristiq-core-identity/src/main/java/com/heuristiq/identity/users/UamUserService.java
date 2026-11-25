package com.heuristiq.identity.users;

import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.UpdateMyProfileRequest;
import com.heuristiq.identity.dto.UpsertUserRequest;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.feature.management.ManagementPageSupport;
import com.heuristiq.identity.helper.RName;
import com.heuristiq.identity.helper.PersonNames;
import com.heuristiq.identity.helper.PhoneIdentity;
import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.metrics.UamMetrics;
import com.heuristiq.identity.role.CallerPermissions;
import com.heuristiq.identity.role.RoleRules;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.security.TenantGuard;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.repository.UamUserRepository;
import com.heuristiq.identity.users.repository.UamUserSearchCriteria;
import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.biometric.application.BiometricRevocationUseCase;
import com.heuristiq.identity.biometric.application.BiometricRevocations;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UamUserService implements UamUserServiceUseCase {

    private final UamUserRepository userRepository;
    private final UserCredentialServiceUseCase userCredentialService;
    private final IdentityProviderSyncService identityProviderSyncService;
    private final UamRoleRepository roleRepository;
    private final UamMetrics metrics;
    private final PasswordMinimumLookup passwordMinimum;
    /** Suspension and deletion end the account's fingerprint / Face ID devices (resolved lazily: no bean cycle). */
    private final ObjectProvider<BiometricRevocationUseCase> biometricRevocation;

    /** With the platform default password minimum (no security policy lookup). */
    public UamUserService(UamUserRepository userRepository,
                          UserCredentialServiceUseCase userCredentialService,
                          IdentityProviderSyncService identityProviderSyncService,
                          UamRoleRepository roleRepository,
                          UamMetrics metrics) {
        this(userRepository, userCredentialService, identityProviderSyncService, roleRepository, metrics,
                tenantId -> UamUserConstants.DEFAULT_MIN_PASSWORD_LENGTH);
    }

    /** Without biometric revocation (tests that do not need it). */
    public UamUserService(UamUserRepository userRepository,
                          UserCredentialServiceUseCase userCredentialService,
                          IdentityProviderSyncService identityProviderSyncService,
                          UamRoleRepository roleRepository,
                          UamMetrics metrics,
                          PasswordMinimumLookup passwordMinimum) {
        this(userRepository, userCredentialService, identityProviderSyncService, roleRepository, metrics, passwordMinimum, null);
    }

    @Autowired
    public UamUserService(UamUserRepository userRepository,
                          UserCredentialServiceUseCase userCredentialService,
                          IdentityProviderSyncService identityProviderSyncService,
                          UamRoleRepository roleRepository,
                          UamMetrics metrics,
                          PasswordMinimumLookup passwordMinimum,
                          ObjectProvider<BiometricRevocationUseCase> biometricRevocation) {
        this.biometricRevocation = biometricRevocation;
        this.passwordMinimum = passwordMinimum;
        this.metrics = metrics;
        this.userRepository = userRepository;
        this.userCredentialService = userCredentialService;
        this.identityProviderSyncService = identityProviderSyncService;
        this.roleRepository = roleRepository;
    }

    public CreateOutput create(CreateInput input) {
        return metrics.recordUserCreated(() -> doCreate(input));
    }

    private CreateOutput doCreate(CreateInput input) {
        UpsertUserRequest request = input.request();
        String username = normalizeRequired(request.username(), "Username is required");
        ensureUsernameAvailable(username, null);
        UserInputValidator.requireValidEmail(request.email());
        ensureEmailAvailable(request.email(), null);
        // Both stored user models require a phone number; without this check the account is rejected only when the
        // transaction commits and the caller gets a 500 instead of a clear 400.
        if (!StringUtils.hasText(request.phoneNumber())) {
            throw new UamException("Phone number is required");
        }
        ensurePhoneAvailable(request.phoneNumber(), null);
        if (!StringUtils.hasText(request.password())) {
            throw new UamException("Password is required");
        }
        List<String> grantedRoles = normalizeCodes(request.roles(), request.userType());
        requireRolesUsableHere(rolesToCheck(request, grantedRoles), List.of());
        requireUserTypeUsableHere(request.userType());
        UserInputValidator.requireStrongPassword(request.password(), passwordMinimum.minimumLength(TenantContext.get()));
        UamUser user = NewUamUserBuilder.newUser()
                .userName(username)
                .email(normalizeOptional(request.email()))
                .phoneNumber(toPhoneNumber(request.phoneNumber()))
                .fullName(toFullName(request.fullName()))
                .status(request.status())
                .roles(grantedRoles)
                // An account created by an organization's admin belongs to that organization (the session's, from the
                // token); without it the account has no owner and is invisible to organization-scoped lists and search.
                .tenantId(TenantContext.get())
                .build();
        applyName(user, user.getFullName());
        UamUser saved = userRepository.save(user);
        userCredentialService.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(saved.getId(), request.password(), false));
        identityProviderSyncService.syncUser(saved);
        return new CreateOutput(saved);
    }

    public UpdateOutput update(UpdateInput input) {
        String id = input.id();
        UpsertUserRequest request = input.request();
        UamUser user = get(new GetInput(id)).user();
        String username = normalizeRequired(request.username(), "Username is required");
        ensureUsernameAvailable(username, id);
        UserInputValidator.requireValidEmail(request.email());
        ensureEmailAvailable(request.email(), id);
        ensurePhoneAvailable(request.phoneNumber(), id);
        if (StringUtils.hasText(request.password())) {
            UserInputValidator.requireStrongPassword(request.password(), passwordMinimum.minimumLength(user.getTenantId()));
        }
        user.setUserName(username);
        user.setEmail(normalizeOptional(request.email()));
        // A phone number is required on every account, so an update that leaves it out keeps the current one
        // (setting it to null would fail the commit with a 500).
        if (StringUtils.hasText(request.phoneNumber())) {
            user.setPhoneNumber(toPhoneNumber(request.phoneNumber()));
        }
        RName newName = toFullName(request.fullName());
        user.setFullName(newName);
        if (newName != null) {
            user.setDisplayName(PersonNames.display(newName));
        }
        user.setStatus(request.status() == null ? user.getStatus() : request.status());
        List<String> grantedRoles = normalizeCodes(request.roles(), request.userType());
        requireRolesUsableHere(rolesToCheck(request, grantedRoles), user.getRoles());
        requireUserTypeUsableHere(request.userType());
        user.setRoles(grantedRoles);
        if (user.getAudit() != null) {
            user.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        revokeBiometricUnlessActive(user);
        UamUser saved = userRepository.save(user);
        if (StringUtils.hasText(request.password())) {
            userCredentialService.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(saved.getId(), request.password(), false));
        }
        identityProviderSyncService.syncUser(saved);
        return new UpdateOutput(saved);
    }

    public AddRolesOutput addRoles(AddRolesInput input) {
        UamUser user = get(new GetInput(input.id())).user();
        requireRolesUsableHere(input.roles(), user.getRoles());
        Set<String> merged = new LinkedHashSet<>(user.getRoles() == null ? List.of() : user.getRoles());
        int before = merged.size();
        input.roles().stream().filter(StringUtils::hasText).map(String::trim).map(String::toUpperCase).forEach(merged::add);
        if (merged.size() == before) {
            return new AddRolesOutput(user);
        }
        user.setRoles(List.copyOf(merged));
        if (user.getAudit() != null) {
            user.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        UamUser saved = userRepository.save(user);
        identityProviderSyncService.syncUser(saved);
        return new AddRolesOutput(saved);
    }

    // Self-service profile update (see UpdateMyProfileRequest) -- only touches fields a user may
    // safely change about themselves; username/roles/status/tenantId are never modified here.
    public UpdateOwnProfileOutput updateOwnProfile(UpdateOwnProfileInput input) {
        String id = input.id();
        UpdateMyProfileRequest request = input.request();
        UamUser user = get(new GetInput(id)).user();
        ensureEmailAvailable(request.email(), id);
        ensurePhoneAvailable(request.phoneNumber(), id);
        if (StringUtils.hasText(request.fullName())) {
            applyName(user, toFullName(request.fullName()));
        }
        if (StringUtils.hasText(request.email())) {
            user.setEmail(normalizeOptional(request.email()));
        }
        if (StringUtils.hasText(request.phoneNumber())) {
            user.setPhoneNumber(toPhoneNumber(request.phoneNumber()));
        }
        if (StringUtils.hasText(request.timezone())) {
            user.setTimezone(request.timezone().trim());
        }
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.profileImage() != null) {
            user.setProfileImage(request.profileImage());
        }
        if (user.getAudit() != null) {
            user.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        UamUser saved = userRepository.save(user);
        identityProviderSyncService.syncUser(saved);
        return new UpdateOwnProfileOutput(saved);
    }

    public UpdateStatusOutput updateStatus(UpdateStatusInput input) {
        UamUser user = get(new GetInput(input.id())).user();
        user.setStatus(input.status());
        if (user.getAudit() != null) {
            user.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        revokeBiometricUnlessActive(user);
        UamUser saved = userRepository.save(user);
        identityProviderSyncService.syncUser(saved);
        return new UpdateStatusOutput(saved);
    }

    /**
     * An account leaving ACTIVE (suspended, disabled, locked, inactive, pending) loses its fingerprint / Face ID devices: a
     * reactivated account enrols again. Runs before the save, so a failure leaves the account unchanged too.
     */
    private void revokeBiometricUnlessActive(UamUser user) {
        if (user.getStatus() != null && user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            BiometricRevocations.revokeAll(biometricRevocation, user.getId(), BiometricRevocationReason.ACCOUNT_SUSPENDED);
        }
    }

    public SetPasswordOutput setPassword(SetPasswordInput input) {
        UamUser user = get(new GetInput(input.id())).user();
        UserInputValidator.requireStrongPassword(input.rawPassword(), passwordMinimum.minimumLength(user.getTenantId()));
        userCredentialService.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(
                user.getId(), input.rawPassword(), input.temporary()));
        identityProviderSyncService.setUserPassword(user, input.rawPassword(), input.temporary());
        return new SetPasswordOutput(user);
    }

    public SetTotpRequiredOutput setTotpRequired(SetTotpRequiredInput input) {
        UamUser user = get(new GetInput(input.id())).user();
        identityProviderSyncService.setTotpRequired(user, input.required());
        return new SetTotpRequiredOutput(userRepository.save(user));
    }

    public GetOutput get(GetInput input) {
        UamUser user = userRepository.findById(input.id()).orElseThrow(() -> UamException.notFound("User not found"));
        TenantGuard.assertAccessible(user.getTenantId());
        return new GetOutput(user);
    }

    @Override
    public FindByIdsOutput findByIds(FindByIdsInput input) {
        Collection<String> requested = input.ids() == null ? List.of() : input.ids();
        Set<String> ids = new LinkedHashSet<>();
        for (String id : requested) {
            if (StringUtils.hasText(id)) {
                ids.add(id.trim());
            }
        }
        if (ids.size() > UamUserConstants.MAX_BATCH_LOOKUP) {
            throw new UamException("At most " + UamUserConstants.MAX_BATCH_LOOKUP + " ids can be looked up at once");
        }
        if (ids.isEmpty()) {
            return new FindByIdsOutput(List.of());
        }
        List<UamUser> visible = userRepository.findAllByIds(ids).stream().filter(UamUserService::accessible).toList();
        return new FindByIdsOutput(visible);
    }

    /** A user outside the caller's tenant is left out of a batch (a single lookup would answer 404). */
    private static boolean accessible(UamUser user) {
        try {
            TenantGuard.assertAccessible(user.getTenantId());
            return true;
        } catch (UamException e) {
            return false;
        }
    }

    /** For sibling-service linkage (e.g. AdminAPI resolving a driver's UAM identity by phone
     *  during approval) -- unlike get(id), a miss is an expected, valid outcome, not an error. */
    public FindByPhoneOutput findByPhone(FindByPhoneInput input) {
        return new FindByPhoneOutput(userRepository.findByPhone(input.phone()));
    }

    public ListOutput list(ListInput input) {
        return new ListOutput(userRepository.findAll(Sort.by(Sort.Direction.ASC, "userName")));
    }

    public ListPageOutput list(ListPageInput input) {
        String scope = sessionTenantScope(null);
        if (scope != null) {
            // An organization's session lists its own people only, never every account of the platform.
            return new ListPageOutput(PageResult.from(userRepository.search(UamUserSearchCriteria.of(null, null, null, scope),
                    ManagementPageSupport.page(input.page(), input.size(), "userName"))));
        }
        return new ListPageOutput(PageResult.from(userRepository.findAll(
                ManagementPageSupport.page(input.page(), input.size(), "userName"))));
    }

    /**
     * The organization a user search is limited to: for a signed-in caller who is not a platform administrator it is always the
     * session's organization (the token), whatever the request asked for, so a forged or missing {@code tenantScope} cannot widen it.
     * A platform administrator, and work with no session, may name any organization or none.
     */
    private String sessionTenantScope(String requested) {
        if (TenantGuard.isSignedInUser() && TenantGuard.isSignedInNonSuperAdmin() && TenantContext.get() != null) {
            return TenantContext.get();
        }
        return requested;
    }

    public SearchOutput search(SearchInput input) {
        UamUserSearchCriteria criteria = UamUserSearchCriteria.of(
                input.roleCode(), input.status(), input.search(), sessionTenantScope(input.tenantScope()));
        return new SearchOutput(PageResult.from(userRepository.search(
                criteria, ManagementPageSupport.page(input.page(), input.size(), "userName"))));
    }

    public DeleteOutput delete(DeleteInput input) {
        UamUser user = get(new GetInput(input.id())).user();
        BiometricRevocations.revokeAll(biometricRevocation, user.getId(), BiometricRevocationReason.ACCOUNT_DELETED);
        identityProviderSyncService.deleteUser(user);
        userRepository.delete(user);
        return new DeleteOutput();
    }

    private void ensureUsernameAvailable(String username, String currentId) {
        userRepository.findByUserNameIgnoreCase(username)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw UamException.conflict("Username already exists");
                });
    }

    private void ensureEmailAvailable(String email, String currentId) {
        String normalized = normalizeOptional(email);
        if (normalized == null) {
            return;
        }
        userRepository.findByEmailIgnoreCase(normalized)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw UamException.conflict("Email already exists");
                });
    }

    private void ensurePhoneAvailable(String phone, String currentId) {
        String normalized = normalizeOptional(phone);
        if (normalized == null) {
            return;
        }
        userRepository.findByPhone(normalized)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw UamException.conflict("Phone number already exists");
                });
    }

    private RPhoneNumber toPhoneNumber(String phone) {
        String normalized = normalizeOptional(phone);
        return normalized == null ? null : PhoneIdentity.toStored(normalized);
    }

    /** The typed name as the stored parts: split at the last space (first name before it, last name after it), one word stays a first name. */
    private RName toFullName(String fullName) {
        return PersonNames.split(fullName);
    }

    /** Sets the name and, from it, the display name shown in lists and messages; a null name leaves both as they were. */
    private static void applyName(UamUser user, RName name) {
        if (name == null) {
            return;
        }
        user.setFullName(name);
        user.setDisplayName(PersonNames.display(name));
    }

    /** A role owned by another organization cannot be given from this one (shared roles and unknown codes are left to the existing rules). */
    /** A user type becomes a role when no roles are given, so the platform administrator type is held to the same rule as the role. */
    private void requireUserTypeUsableHere(UamEnums.UserType userType) {
        if (userType == UamEnums.UserType.SUPER_ADMIN && TenantGuard.isSignedInNonSuperAdmin()) {
            throw new UamException("Only a super admin can create a " + UamCommonConstants.SUPER_ADMIN_ROLE + " user");
        }
    }

    /**
     * Every role newly given must be usable by this session: not the platform administrator role, not another organization's, and
     * (for anyone but a platform administrator) carrying no privilege the caller does not hold, so nobody can raise themselves or
     * another account above their own access by handing out a broader role. Roles the account already has are not re-checked.
     *
     * @param alreadyHeld the codes the account has today (null or empty on create)
     */
    private void requireRolesUsableHere(Collection<String> codes, Collection<String> alreadyHeld) {
        if (codes == null) {
            return;
        }
        Set<String> existing = new java.util.HashSet<>();
        if (alreadyHeld != null) {
            alreadyHeld.stream().filter(StringUtils::hasText).map(code -> code.trim().toUpperCase()).forEach(existing::add);
        }
        boolean checkHeld = TenantGuard.isSignedInUser() && TenantGuard.isSignedInNonSuperAdmin();
        Set<String> callerHeld = checkHeld ? CallerPermissions.held(roleRepository) : Set.of();
        codes.stream().filter(StringUtils::hasText).forEach(code -> {
            // The platform administrator role is shared by every organization, so the organization check cannot protect it:
            // only a platform administrator may hand it out (an organization's administrator gets their own organization's role).
            if (UamCommonConstants.SUPER_ADMIN_ROLE.equalsIgnoreCase(code.trim()) && TenantGuard.isSignedInNonSuperAdmin()) {
                throw new UamException("Only a super admin can give the " + UamCommonConstants.SUPER_ADMIN_ROLE + " role");
            }
            roleRepository.findByCodeIgnoreCase(code.trim()).ifPresent(role -> {
                TenantGuard.assertAccessible(role.getTenantId());
                if (checkHeld && !existing.contains(code.trim().toUpperCase()) && role.getPermissions() != null) {
                    RoleRules.requireHeld(role.getPermissions(), callerHeld);
                }
            });
        });
    }

    /**
     * The codes to check: the ones the caller typed, or, when none were typed, the role the user type turns into, so the
     * {@code userType} shortcut cannot hand out a role the explicit list would have been refused.
     */
    private static Collection<String> rolesToCheck(UpsertUserRequest request, List<String> derived) {
        boolean typed = request.roles() != null && request.roles().stream().anyMatch(StringUtils::hasText);
        return typed ? request.roles() : derived;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new UamException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeCodes(List<String> values, UamEnums.UserType userType) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty() && userType != null) {
            normalized.add(userType.name());
        }
        return List.copyOf(normalized);
    }
}
