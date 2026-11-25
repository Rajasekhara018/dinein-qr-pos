package com.heuristiq.identity.organization.membership;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.identity.biometric.application.BiometricRevocationUseCase;
import com.heuristiq.identity.biometric.application.BiometricRevocations;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.organization.PlatformOrganizationProperties;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.security.TenantGuard;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.util.UserTypeResolver;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserSearchCriteria;
import com.heuristiq.identity.users.repository.UamUserRepository;

@Service
public class TenantMembershipService implements TenantMembershipServiceUseCase {
    private static final Logger log = LoggerFactory.getLogger(TenantMembershipService.class);

    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final UamUserRepository userRepository;
    private final PlatformOrganizationProperties platformOrganization;
    private final Clock clock;
    /** Removal from an organization ends the person's fingerprint / Face ID devices (resolved lazily: no bean cycle). */
    private final ObjectProvider<BiometricRevocationUseCase> biometricRevocation;

    /** Without biometric revocation (tests that do not need it). */
    public TenantMembershipService(TenantMembershipRepository membershipRepository, TenantRepository tenantRepository,
                                   UamUserRepository userRepository, PlatformOrganizationProperties platformOrganization) {
        this(membershipRepository, tenantRepository, userRepository, platformOrganization, Clock.systemUTC(), null);
    }

    @Autowired
    public TenantMembershipService(TenantMembershipRepository membershipRepository, TenantRepository tenantRepository,
                                   UamUserRepository userRepository, PlatformOrganizationProperties platformOrganization,
                                   ObjectProvider<BiometricRevocationUseCase> biometricRevocation) {
        this(membershipRepository, tenantRepository, userRepository, platformOrganization, Clock.systemUTC(), biometricRevocation);
    }

    /** Lets tests supply a fixed clock; UAM defines no {@code Clock} bean. */
    TenantMembershipService(TenantMembershipRepository membershipRepository, TenantRepository tenantRepository,
                            UamUserRepository userRepository, PlatformOrganizationProperties platformOrganization, Clock clock) {
        this(membershipRepository, tenantRepository, userRepository, platformOrganization, clock, null);
    }

    TenantMembershipService(TenantMembershipRepository membershipRepository, TenantRepository tenantRepository,
                            UamUserRepository userRepository, PlatformOrganizationProperties platformOrganization, Clock clock,
                            ObjectProvider<BiometricRevocationUseCase> biometricRevocation) {
        this.biometricRevocation = biometricRevocation;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.platformOrganization = platformOrganization;
        this.clock = clock;
    }

    @Override
    public Page<MemberView> listMembers(ListInput input) {
        Tenant tenant = requireTenant(input.tenantId());
        TenantGuard.assertAccessible(tenant.getId());
        if (StringUtils.hasText(input.search())) {
            return searchMembers(tenant.getId(), input.search().trim());
        }
        Page<TenantMembership> page = membershipRepository.findByTenantId(tenant.getId(), input.pageable());
        Map<String, UamUser> users = userRepository
                .findAllByIds(page.getContent().stream().map(TenantMembership::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(UamUser::getId, Function.identity(), (a, b) -> a));
        return page.map(membership -> {
            UamUser user = users.get(membership.getUserId());
            return new MemberView(membership, user == null ? null : user.getUserName(), user == null ? null : user.getEmail(),
                    user == null ? null : user.getDisplayName(), user == null ? null : user.getEmployeeCode(),
                    user == null ? null : UserTypeResolver.resolve(user.getRoles()));
        });
    }

    /** Active members whose account matches the text: the account search runs first, then only those who belong here are kept. */
    private Page<MemberView> searchMembers(String tenantId, String text) {
        List<MemberView> found = new ArrayList<>();
        for (UamUser user : userRepository.search(UamUserSearchCriteria.of(null, null, text, null),
                PageRequest.of(0, TenancyConstants.MEMBER_SEARCH_LIMIT)).getContent()) {
            membershipRepository.findByTenantIdAndUserId(tenantId, user.getId())
                    .filter(membership -> membership.getStatus() == TenantMembershipStatus.ACTIVE)
                    .ifPresent(membership -> found.add(new MemberView(membership, user.getUserName(), user.getEmail(), user.getDisplayName(), user.getEmployeeCode(),
                            UserTypeResolver.resolve(user.getRoles()))));
        }
        return new PageImpl<>(found);
    }

    @Override
    public TenantMembership addMember(AddInput input) {
        Tenant tenant = requireTenant(input.tenantId());
        if (tenant.getStatus() == TenantStatus.ARCHIVED) {
            throw new UamException("This organization is archived, so members cannot be added to it");
        }
        String userId = requireText(input.request().userId(), "User id");
        if (userRepository.findById(userId).isEmpty()) {
            throw UamException.notFound("User not found: " + userId);
        }
        TenantMembership existing = membershipRepository.findByTenantIdAndUserId(tenant.getId(), userId).orElse(null);
        if (existing != null && existing.getStatus() == TenantMembershipStatus.ACTIVE) {
            return existing;
        }
        requireRoomForAnotherMember(tenant);
        LocalDateTime now = LocalDateTime.now(clock);
        TenantMembership membership = existing != null ? existing : TenantMembership.builder()
                .tenantId(tenant.getId()).userId(userId).addedBy(input.actor()).joinedAt(now).build();
        membership.setStatus(TenantMembershipStatus.ACTIVE);
        membership.setUpdatedBy(input.actor());
        membership.setUpdatedAt(now);
        return membershipRepository.save(membership);
    }

    @Override
    public TenantMembership updateMember(UpdateInput input) {
        Tenant tenant = requireTenant(input.tenantId());
        TenantMembership membership = membershipRepository.findByTenantIdAndUserId(tenant.getId(), input.userId())
                .orElseThrow(() -> UamException.notFound("This person is not a member of the organization"));
        TenantMembershipStatus status = input.request().status();
        if (status != membership.getStatus()) {
            if (status == TenantMembershipStatus.ACTIVE) {
                requireRoomForAnotherMember(tenant);
            }
            if (status == TenantMembershipStatus.REMOVED) {
                // Before the save, so a failure leaves the membership as it was.
                BiometricRevocations.revokeAll(biometricRevocation, membership.getUserId(), BiometricRevocationReason.MEMBERSHIP_REMOVED);
            }
            membership.setStatus(status);
            membership.setUpdatedBy(input.actor());
            membership.setUpdatedAt(LocalDateTime.now(clock));
            membership = membershipRepository.save(membership);
        }
        return membership;
    }

    @Override
    public java.util.Set<String> activeMembersAmong(String tenantId, java.util.Collection<String> userIds) {
        java.util.Set<String> active = new java.util.LinkedHashSet<>();
        if (tenantId == null || tenantId.isBlank() || userIds == null) {
            return active;
        }
        userIds.stream().filter(java.util.Objects::nonNull).distinct().limit(MAX_MEMBERSHIP_CHECK).forEach(id ->
                membershipRepository.findByTenantIdAndUserId(tenantId, id)
                        .filter(m -> m.getStatus() == TenantMembershipStatus.ACTIVE).ifPresent(m -> active.add(id)));
        return active;
    }

    @Override
    public List<OrganizationView> organizationsOf(String userId) {
        List<OrganizationView> organizations = new ArrayList<>();
        if (userRepository.findById(userId).map(TenantMembershipService::isSuperAdmin).orElse(false)) {
            // A platform administrator manages every organization, so may open any active one without being a member.
            for (Tenant tenant : tenantRepository.findAll(Sort.by("name"))) {
                if (tenant.getStatus() == null || tenant.getStatus() == TenantStatus.ACTIVE) {
                    organizations.add(OrganizationView.of(tenant, TenantMembershipStatus.ACTIVE));
                }
            }
            return organizations;
        }
        for (TenantMembership membership : sortedMemberships(userId)) {
            if (membership.getStatus() == TenantMembershipStatus.REMOVED) {
                continue;
            }
            tenantRepository.findById(membership.getTenantId()).ifPresent(tenant ->
                    organizations.add(OrganizationView.of(tenant, membership.getStatus())));
        }
        return organizations;
    }

    @Override
    public String resolveForLogin(UamUser user, String requestedOrganization) {
        List<TenantMembership> memberships = sortedMemberships(user.getId());
        if (memberships.isEmpty()) {
            Optional<TenantMembership> created = createFirstMembership(user);
            if (created.isEmpty()) {
                return user.getTenantId();
            }
            memberships = List.of(created.get());
        }
        Set<String> workable = new HashSet<>();
        for (TenantMembership membership : memberships) {
            if (membership.getStatus() == TenantMembershipStatus.ACTIVE && isWorkable(membership.getTenantId())) {
                workable.add(membership.getTenantId());
            }
        }
        if (StringUtils.hasText(requestedOrganization)) {
            Tenant requested = findTenant(requestedOrganization.trim())
                    .orElseThrow(() -> new UamException("You are not a member of this organization", HttpStatus.FORBIDDEN));
            if (isSuperAdmin(user) && isWorkable(requested.getId())) {
                return requested.getId();
            }
            if (!workable.contains(requested.getId())) {
                throw new UamException("You are not a member of this organization", HttpStatus.FORBIDDEN);
            }
            return requested.getId();
        }
        if (workable.isEmpty()) {
            throw new UamException("You do not belong to any active organization", HttpStatus.FORBIDDEN);
        }
        if (user.getTenantId() != null && workable.contains(user.getTenantId())) {
            return user.getTenantId();
        }
        Optional<Tenant> defaultTenant = tenantRepository.findByCodeIgnoreCase(platformOrganization.code());
        if (defaultTenant.isPresent() && workable.contains(defaultTenant.get().getId())) {
            return defaultTenant.get().getId();
        }
        return memberships.stream().map(TenantMembership::getTenantId).filter(workable::contains).findFirst().orElseThrow();
    }

    @Override
    public String ensureAnyMembership(UamUser user) {
        List<TenantMembership> existing = sortedMemberships(user.getId());
        if (!existing.isEmpty()) {
            return existing.get(0).getTenantId();
        }
        return createFirstMembership(user).map(TenantMembership::getTenantId).orElse(null);
    }

    @Override
    public int backfillMemberships() {
        int created = 0;
        int pageNumber = 0;
        Page<UamUser> page;
        do {
            page = userRepository.findAll(PageRequest.of(pageNumber++, TenancyConstants.BACKFILL_PAGE_SIZE));
            for (UamUser user : page.getContent()) {
                if (membershipRepository.findByUserId(user.getId()).isEmpty()) {
                    Optional<TenantMembership> membership = createFirstMembership(user);
                    if (membership.isPresent()) {
                        created++;
                        adoptIntoOrganization(user, membership.get().getTenantId());
                    }
                }
            }
        } while (page.hasNext());
        if (created > 0) {
            log.info("Gave {} existing accounts a membership of their organization", created);
        }
        return created;
    }

    /**
     * A customer, driver or vendor account with no organization of its own gets the one it was just made a member of, so
     * organization-scoped lists and search can find it. Staff accounts without an organization are left alone: they are
     * the platform's own (shared) accounts.
     */
    private void adoptIntoOrganization(UamUser user, String tenantId) {
        if (user.getTenantId() == null && UserTypeResolver.resolve(user.getRoles()) != UamEnums.UserType.OPS) {
            user.setTenantId(tenantId);
            userRepository.save(user);
        }
    }

    /** The account's own organization when it names one that exists, otherwise the default organization. */
    private Optional<TenantMembership> createFirstMembership(UamUser user) {
        Optional<Tenant> tenant = Optional.ofNullable(user.getTenantId()).flatMap(tenantRepository::findById)
                .or(() -> tenantRepository.findByCodeIgnoreCase(platformOrganization.code()));
        if (tenant.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return Optional.of(membershipRepository.save(TenantMembership.builder().tenantId(tenant.get().getId()).userId(user.getId())
                .addedBy(TenancyConstants.SYSTEM_ACTOR).joinedAt(now).updatedBy(TenancyConstants.SYSTEM_ACTOR).updatedAt(now).build()));
    }

    private List<TenantMembership> sortedMemberships(String userId) {
        return membershipRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(TenantMembership::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static boolean isSuperAdmin(UamUser user) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(TenancyConstants.SUPER_ADMIN_ROLE::equalsIgnoreCase);
    }

    private boolean isWorkable(String tenantId) {
        return tenantRepository.findById(tenantId).map(t -> t.getStatus() == null || t.getStatus() == TenantStatus.ACTIVE).orElse(false);
    }

    private Optional<Tenant> findTenant(String codeOrId) {
        return tenantRepository.findById(codeOrId).or(() -> tenantRepository.findByCodeIgnoreCase(codeOrId));
    }

    /** An organization with a member limit may not hold more active members than that. */
    private void requireRoomForAnotherMember(Tenant tenant) {
        Integer limit = tenant.getMaxUsers();
        if (limit == null || limit <= 0) {
            return;
        }
        if (membershipRepository.countByTenantIdAndStatus(tenant.getId(), TenantMembershipStatus.ACTIVE) >= limit) {
            throw UamException.conflict("This organization already has its limit of " + limit + " members");
        }
    }

    private Tenant requireTenant(String tenantId) {
        return tenantRepository.findById(requireText(tenantId, "Organization id"))
                .orElseThrow(() -> UamException.notFound("Organization not found: " + tenantId));
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new UamException(label + " is required");
        }
        return value.trim();
    }
}
