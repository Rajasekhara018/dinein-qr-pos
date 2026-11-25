package com.heuristiq.identity.application.access;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.identity.application.Application;
import com.heuristiq.identity.application.access.repository.ApplicationAccessRepository;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

@Service
public class ApplicationAccessService implements ApplicationAccessServiceUseCase {
    private final ApplicationAccessRepository accessRepository;
    private final ApplicationRepository applicationRepository;
    private final UamUserRepository userRepository;
    private final Clock clock;
    private com.heuristiq.identity.role.repository.UamRoleRepository roleRepository;

    @Autowired
    public ApplicationAccessService(ApplicationAccessRepository accessRepository,
                                    ApplicationRepository applicationRepository,
                                    UamUserRepository userRepository) {
        this(accessRepository, applicationRepository, userRepository, Clock.systemUTC());
    }

    /** Lets tests supply a fixed clock; UAM defines no {@code Clock} bean. */
    ApplicationAccessService(ApplicationAccessRepository accessRepository,
                             ApplicationRepository applicationRepository,
                             UamUserRepository userRepository,
                             Clock clock) {
        this.accessRepository = accessRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public GrantOutput grant(GrantInput input) {
        GrantApplicationAccessRequest request = input.request();
        String applicationCode = normalize(request.applicationCode(), "Application code");
        Application application = applicationRepository.findByCodeIgnoreCase(applicationCode)
                .orElseThrow(() -> UamException.notFound("Application not found: " + applicationCode));
        if (Boolean.FALSE.equals(application.getActive())) {
            throw new UamException("Application is switched off: " + applicationCode);
        }

        AccessSubjectType type = request.subjectType();
        String subjectId = type == AccessSubjectType.ROLE
                ? normalize(request.subjectId(), "Role name")
                : requireText(request.subjectId(), "User id");
        if (type == AccessSubjectType.USER && userRepository.findById(subjectId).isEmpty()) {
            throw UamException.notFound("User not found: " + subjectId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ApplicationAccess access = accessRepository.findBySubject(applicationCode, type, subjectId)
                .orElseGet(() -> ApplicationAccess.builder()
                        .applicationCode(applicationCode)
                        .subjectType(type)
                        .subjectId(subjectId)
                        .build());
        if (access.getId() != null && access.isActive()) {
            return new GrantOutput(access);
        }
        access.setActive(true);
        access.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        access.setGrantedBy(input.grantedBy());
        access.setGrantedAt(now);
        access.setRevokedBy(null);
        access.setRevokedAt(null);
        return new GrantOutput(accessRepository.save(access));
    }

    @Override
    public RevokeOutput revoke(RevokeInput input) {
        ApplicationAccess access = accessRepository.findById(input.id())
                .orElseThrow(() -> UamException.notFound("Application access grant not found"));
        if (!access.isActive()) {
            return new RevokeOutput(access);
        }
        access.setActive(false);
        access.setRevokedBy(input.revokedBy());
        access.setRevokedAt(LocalDateTime.now(clock));
        return new RevokeOutput(accessRepository.save(access));
    }

    @Override
    public ListOutput listForApplication(ListForApplicationInput input) {
        return new ListOutput(accessRepository.findByApplicationCode(normalize(input.applicationCode(), "Application code")));
    }

    @Override
    public ListOutput listForSubject(ListForSubjectInput input) {
        String subjectId = input.subjectType() == AccessSubjectType.ROLE
                ? normalize(input.subjectId(), "Role name")
                : requireText(input.subjectId(), "User id");
        return new ListOutput(accessRepository.findBySubjectTypeAndSubjectId(input.subjectType(), subjectId));
    }

    /** Optional: lets an organization's administrator role open every registered application. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRoleRepository(com.heuristiq.identity.role.repository.UamRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /** True for the administrator role an organization owns (its code ends with _ADMIN and it belongs to an organization). */
    private boolean isOrganizationAdminRole(String roleCode) {
        return roleRepository != null && roleCode.endsWith(com.heuristiq.identity.organization.admin.OrganizationAdminConstants.ADMIN_ROLE_SUFFIX)
                && roleRepository.findByCodeIgnoreCase(roleCode).filter(r -> StringUtils.hasText(r.getTenantId())).isPresent();
    }

    @Override
    public ResolveOutput resolveAccessibleApplications(ResolveInput input) {
        UamUser user = input.user();
        Set<String> candidates = new TreeSet<>();
        if (StringUtils.hasText(user.getId())) {
            accessRepository.findActiveBySubjects(AccessSubjectType.USER, List.of(user.getId()))
                    .forEach(grant -> candidates.add(grant.getApplicationCode()));
        }
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles().stream()
                .filter(StringUtils::hasText)
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        accessRepository.findActiveBySubjects(AccessSubjectType.ROLE, roles)
                .forEach(grant -> candidates.add(grant.getApplicationCode()));
        if (roles.contains(ApplicationAccessConstants.SUPER_ADMIN_ROLE) || roles.stream().anyMatch(this::isOrganizationAdminRole)) {
            // A super admin, and an organization's administrator, may open every registered application, so a newly registered one needs no extra grant.
            applicationRepository.findAll(Sort.by("code")).forEach(app -> candidates.add(app.getCode()));
        }

        List<String> usable = candidates.stream()
                .filter(code -> applicationRepository.findByCodeIgnoreCase(code)
                        .map(app -> !Boolean.FALSE.equals(app.getActive()))
                        .orElse(false))
                .toList();
        return new ResolveOutput(usable);
    }

    @Override
    public ResolveOutput resolveAccessibleApplicationsForUserId(ResolveForUserIdInput input) {
        UamUser user = userRepository.findById(input.userId())
                .orElseThrow(() -> UamException.notFound("User not found"));
        return resolveAccessibleApplications(new ResolveInput(user));
    }

    private String normalize(String value, String label) {
        return requireText(value, label).toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new UamException(label + " is required");
        }
        return value.trim();
    }
}
