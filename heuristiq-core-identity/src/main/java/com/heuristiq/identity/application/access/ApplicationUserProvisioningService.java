package com.heuristiq.identity.application.access;

import com.heuristiq.identity.common.UamCommonConstants;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.IdentityRegisterRequest;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.organization.membership.AddMemberRequest;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.registration.UserRegistrationServiceUseCase;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

@Service
public class ApplicationUserProvisioningService implements ApplicationUserProvisioningServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.identity.securitypolicy.SecurityPolicyPasswordGenerator policyPasswords;
    private static final Logger log = LoggerFactory.getLogger(ApplicationUserProvisioningService.class);

    private final UamUserRepository userRepository;
    private final UserRegistrationServiceUseCase registrationService;
    private final ApplicationAccessServiceUseCase accessService;
    private final UserCredentialServiceUseCase credentials;
    private final TenantMembershipServiceUseCase memberships;
    private final TenantRepository tenants;
    private final TenantApplicationServiceUseCase subscriptions;
    private final SecureRandom random = new SecureRandom();

    public ApplicationUserProvisioningService(UamUserRepository userRepository,
                                              UserRegistrationServiceUseCase registrationService,
                                              ApplicationAccessServiceUseCase accessService,
                                              UserCredentialServiceUseCase credentials,
                                              TenantMembershipServiceUseCase memberships,
                                              TenantRepository tenants,
                                              TenantApplicationServiceUseCase subscriptions) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.accessService = accessService;
        this.credentials = credentials;
        this.memberships = memberships;
        this.tenants = tenants;
        this.subscriptions = subscriptions;
    }

    /**
     * Finds or creates the account and grants it the application. When the request names an organization
     * ({@code tenantId}) the person is made an ACTIVE member of THAT organization; it must exist, be ACTIVE and hold an
     * active subscription to the application, all checked before anything is created. With no organization named the
     * account keeps today's behaviour (its own organization, or the default one).
     *
     * <p>Taking the organization from the request body is acceptable only because this use case is reached solely
     * through the internal, service-token protected endpoint, whose caller (the onboarding service) is trusted and
     * passes the organization the submission was created in. A public endpoint must never take a tenantId from a body.
     */
    @Override
    public ProvisionOutput provision(ProvisionInput input) {
        ProvisionApplicationUserRequest request = input.request();
        if (ApplicationAccessConstants.PRIVILEGED_TYPES.contains(request.userType())) {
            throw new UamException("Back-office accounts cannot be created through onboarding");
        }
        // Validated first, so a wrong organization creates no account, grant or membership.
        String requestedOrganization = requireOnboardableOrganization(request);
        String mobile = request.mobile().trim();
        String email = request.email().trim();

        Optional<UamUser> existing = find(mobile, email);
        // true only when THIS call made the account: a request that lost a race to create it must not touch the credentials
        boolean[] createdHere = {false};
        UamUser user = existing.orElseGet(() -> createOrFindAfterRace(request, mobile, email, createdHere));
        boolean created = existing.isEmpty() && createdHere[0];
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw new UamException("This account is not active, so application access cannot be granted");
        }

        // A new account gets a random one-time password, so the person can sign in with their email and it right after
        // approval. It is marked temporary (changed at first sign-in) and returned once to the caller that tells them.
        // An account that already existed keeps whatever credentials it has.
        String temporaryPassword = null;
        if (created) {
            temporaryPassword = policyPasswords == null ? randomPassword() : policyPasswords.generate(user,
                    requestedOrganization == null ? user.getTenantId() : requestedOrganization, user.getRoles(), List.of(request.applicationCode()));
            credentials.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(user.getId(), temporaryPassword, true));
        }

        // Granting is idempotent, so a repeat after a failure here simply finds the account and finishes the job.
        accessService.grant(new ApplicationAccessServiceUseCase.GrantInput(
                new GrantApplicationAccessRequest(request.applicationCode(), AccessSubjectType.USER, user.getId(), ApplicationAccessConstants.GRANT_NOTE),
                ApplicationAccessConstants.GRANTED_BY));
        // One account per person: onboarding the same person into another organization adds a membership to the account
        // they already have (idempotent: an active member stays as they are), instead of creating a second account.
        String organization;
        if (requestedOrganization != null) {
            organization = requestedOrganization;
            memberships.addMember(new TenantMembershipServiceUseCase.AddInput(
                    organization, new AddMemberRequest(user.getId()), ApplicationAccessConstants.GRANTED_BY));
        } else {
            // No organization named: the account still belongs to one (its own, or the default), so it shows in the
            // organization's lists and can sign in without waiting for the next start's backfill.
            organization = memberships.ensureAnyMembership(user);
        }
        // An account with no organization of its own is invisible to organization-scoped lists and to the search the
        // org builder's people picker uses, so it takes the one it was onboarded into. One that already has an
        // organization keeps it: the other organizations reach the person through their membership.
        if (user.getTenantId() == null && organization != null) {
            user.setTenantId(organization);
            userRepository.save(user);
        }
        var applications = accessService.resolveAccessibleApplications(
                new ApplicationAccessServiceUseCase.ResolveInput(user)).applicationCodes();
        log.info("Provisioned application access userId={} application={} accountCreated={}",
                user.getId(), request.applicationCode(), created);
        return new ProvisionOutput(user.getId(), created, applications, temporaryPassword);
    }

    @Override
    public LookupOutput lookup(LookupInput input) {
        String mobile = input.mobile() == null ? "" : input.mobile().trim();
        String email = input.email() == null ? "" : input.email().trim();
        Optional<UamUser> byPhone = mobile.isEmpty() ? Optional.empty() : userRepository.findByPhone(mobile);
        Optional<UamUser> byEmail = email.isEmpty() ? Optional.empty() : userRepository.findByEmailIgnoreCase(email);
        if (byPhone.isEmpty() && byEmail.isEmpty()) {
            return new LookupOutput(false, null, false, false, false, List.of());
        }
        if (byPhone.isPresent() && byEmail.isPresent() && !byPhone.get().getId().equals(byEmail.get().getId())) {
            // Two different people: nothing here may be reused, and nothing about either is revealed.
            return new LookupOutput(true, null, true, true, false, List.of());
        }
        UamUser account = byPhone.isPresent() ? byPhone.get() : byEmail.get();
        List<String> roles = account.getRoles() == null ? List.of() : account.getRoles().stream()
                .filter(role -> role != null && !role.isBlank()).map(role -> role.trim().toUpperCase(java.util.Locale.ROOT)).distinct().toList();
        return new LookupOutput(true, account.getId(), byPhone.isPresent(), byEmail.isPresent(),
                account.getStatus() == UamEnums.UserStatus.ACTIVE, roles);
    }

    @Override
    public IssueTemporaryPasswordOutput issueTemporaryPassword(IssueTemporaryPasswordInput input) {
        UamUser user = userRepository.findById(input.userId())
                .orElseThrow(() -> UamException.notFound("User not found"));
        if (user.getRoles() != null && user.getRoles().stream().anyMatch(role -> ApplicationAccessConstants.PRIVILEGED_TYPES.stream().anyMatch(type -> type.name().equalsIgnoreCase(role)))) {
            throw new UamException("Back-office accounts cannot be given a temporary password this way");
        }
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw new UamException("This account is not active");
        }
        boolean hasPassword = credentials.findActiveCredential(
                new UserCredentialServiceUseCase.FindActiveCredentialInput(user.getId(), UamEnums.AuthProvider.LOCAL)).credential().filter(c -> org.springframework.util.StringUtils.hasText(c.getPasswordHash())).isPresent();
        if (hasPassword) {
            return new IssueTemporaryPasswordOutput(user.getId(), null);
        }
        String temporaryPassword = policyPasswords == null ? randomPassword() : policyPasswords.generate(user,
                user.getTenantId(), user.getRoles(), List.of());
        credentials.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(user.getId(), temporaryPassword, true));
        log.info("Issued a temporary password userId={}", user.getId());
        return new IssueTemporaryPasswordOutput(user.getId(), temporaryPassword);
    }

    /** One character from each class (so any reasonable password policy is met), then random, then shuffled. Look-alike characters are left out so it can be typed from an email. */
    private String randomPassword() {
        String all = UamCommonConstants.PASSWORD_UPPER + UamCommonConstants.PASSWORD_LOWER + UamCommonConstants.PASSWORD_DIGITS + UamCommonConstants.PASSWORD_MESSAGE_SAFE_SYMBOLS;
        StringBuilder out = new StringBuilder(UamCommonConstants.TEMPORARY_PASSWORD_LENGTH);
        for (String pool : new String[] {UamCommonConstants.PASSWORD_UPPER, UamCommonConstants.PASSWORD_LOWER, UamCommonConstants.PASSWORD_DIGITS, UamCommonConstants.PASSWORD_MESSAGE_SAFE_SYMBOLS}) {
            out.append(pool.charAt(random.nextInt(pool.length())));
        }
        while (out.length() < UamCommonConstants.TEMPORARY_PASSWORD_LENGTH) {
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

    /**
     * Creates the account. If another call created the same person a moment earlier the registration answers
     * "already exists" (409); the account is then looked up and used instead of failing the approval.
     */
    private UamUser createOrFindAfterRace(ProvisionApplicationUserRequest request, String mobile, String email, boolean[] createdHere) {
        try {
            String userId = registrationService.register(new UserRegistrationServiceUseCase.RegisterInput(
                    new IdentityRegisterRequest(request.name().trim(), mobile, email, null, request.userType()), true)).response().userId();
            createdHere[0] = true;
            return userRepository.findById(userId)
                    .orElseThrow(() -> new UamException("Account was created but could not be read back", HttpStatus.INTERNAL_SERVER_ERROR));
        } catch (UamException e) {
            if (e.getStatus() == HttpStatus.CONFLICT) {
                return find(mobile, email).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    /**
     * The organization the request names, checked: it exists (404), is ACTIVE (409) and uses the application (409). Null
     * when the request names none. An organization that does not hold the application is refused, never subscribed here.
     */
    private String requireOnboardableOrganization(ProvisionApplicationUserRequest request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return null;
        }
        String tenantId = request.tenantId().trim();
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() ->
                UamException.notFound(ApplicationAccessConstants.MSG_ORGANIZATION_NOT_FOUND.formatted(tenantId)));
        if (tenant.getStatus() != null && tenant.getStatus() != UamEnums.TenantStatus.ACTIVE) {
            throw UamException.conflict(ApplicationAccessConstants.MSG_ORGANIZATION_NOT_ACTIVE.formatted(tenantId, tenant.getStatus()));
        }
        String applicationCode = request.applicationCode().trim();
        boolean subscribed = subscriptions.activeApplicationCodes(tenant.getId()).stream()
                .anyMatch(applicationCode::equalsIgnoreCase);
        if (!subscribed) {
            throw UamException.conflict(ApplicationAccessConstants.MSG_APPLICATION_NOT_SUBSCRIBED.formatted(tenantId, applicationCode));
        }
        return tenant.getId();
    }

    private Optional<UamUser> find(String mobile, String email) {
        Optional<UamUser> byPhone = userRepository.findByPhone(mobile);
        Optional<UamUser> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byPhone.isPresent() && byEmail.isPresent() && !byPhone.get().getId().equals(byEmail.get().getId())) {
            throw UamException.conflict("This mobile number and email belong to different accounts");
        }
        return byPhone.isPresent() ? byPhone : byEmail;
    }
}
