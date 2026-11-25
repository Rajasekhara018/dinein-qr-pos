package com.heuristiq.identity.organization;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.heuristiq.identity.application.ApplicationServiceUseCase;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

/**
 * Makes sure the platform organization ({@link PlatformOrganizationProperties}) exists, owns the bootstrap super admin, and
 * has every existing account as a member, and holds the catalogue applications of {@link DefaultOrganizationConstants#DEFAULT_ORGANIZATION_APPLICATIONS}.
 * Safe to run on every start: it only adds what is missing. Those applications are created from the catalogue here (nothing
 * else seeds application rows), so a fresh database has them linked on its first start; an application an administrator
 * removed from the organization is not given back. Other organizations get applications only when an administrator creates them.
 *
 * <p>An account that has no organization is only given one here when {@code railcab.uam.platform-org.adopt-unowned-users}
 * is on, so a one-off migration is a deliberate switch and never something a restart does on its own.
 */
@Component
@Order(100)
public class DefaultOrganizationBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DefaultOrganizationBootstrap.class);

    private final TenantRepository tenantRepository;
    private final ApplicationServiceUseCase applications;
    private final TenantApplicationServiceUseCase subscriptions;
    private final TenantMembershipServiceUseCase memberships;
    private final UamUserRepository userRepository;
    private final PlatformOrganizationProperties platformOrganization;
    private final String bootstrapAdminUsername;

    public DefaultOrganizationBootstrap(TenantRepository tenantRepository, ApplicationServiceUseCase applications,
                                        TenantApplicationServiceUseCase subscriptions, TenantMembershipServiceUseCase memberships,
                                        UamUserRepository userRepository, PlatformOrganizationProperties platformOrganization,
                                        @Value("${railcab.uam.bootstrap.admin-username:}") String bootstrapAdminUsername) {
        this.tenantRepository = tenantRepository;
        this.applications = applications;
        this.subscriptions = subscriptions;
        this.memberships = memberships;
        this.userRepository = userRepository;
        this.platformOrganization = platformOrganization;
        this.bootstrapAdminUsername = bootstrapAdminUsername;
    }

    @Override
    public void run(String... args) {
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(platformOrganization.code())
                .orElseGet(this::createPlatformOrganization);
        giveDefaultApplications(tenant);
        assignBootstrapAdmin(tenant);
        if (platformOrganization.adoptUnownedUsers()) {
            adoptUnownedUsers(tenant);
        }
        memberships.backfillMemberships();
    }

    /**
     * Links each default application (a link that exists, even a cancelled one, is left alone) and makes sure the application
     * row behind every live link exists. Does not depend on application rows existing first.
     */
    private void giveDefaultApplications(Tenant tenant) {
        for (String code : DefaultOrganizationConstants.DEFAULT_ORGANIZATION_APPLICATIONS) {
            subscriptions.ensureSubscribed(tenant.getId(), code, DefaultOrganizationConstants.SYSTEM_ACTOR);
        }
        List<String> linked = subscriptions.linkedApplicationCodes(tenant.getId());
        DefaultOrganizationConstants.DEFAULT_ORGANIZATION_APPLICATIONS.stream().filter(linked::contains).forEach(applications::ensureCatalogueApplication);
    }

    private Tenant createPlatformOrganization() {
        Tenant created = tenantRepository.save(Tenant.builder()
                .code(platformOrganization.code())
                .name(platformOrganization.name())
                .type(TenantType.INTERNAL)
                .status(TenantStatus.ACTIVE)
                .active(true)
                .systemTenant(true)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build());
        log.info("Created the platform organization {} ({})", created.getCode(), created.getId());
        return created;
    }

    /** The bootstrap super admin belongs to the platform organization, unless it was already placed somewhere. */
    private void assignBootstrapAdmin(Tenant tenant) {
        if (!StringUtils.hasText(bootstrapAdminUsername)) {
            return;
        }
        userRepository.findByUserNameIgnoreCase(bootstrapAdminUsername.trim())
                .filter(admin -> !StringUtils.hasText(admin.getTenantId()))
                .ifPresent(admin -> {
                    admin.setTenantId(tenant.getId());
                    // A startup step must never keep the whole service from starting: an account that fails validation
                    // (for example one created before a phone number was required) is skipped and reported instead.
                    if (saveOrSkip(admin)) {
                        log.info("Placed the bootstrap super admin in the platform organization {}", tenant.getCode());
                    }
                });
    }

    /** Saves the account; one that cannot be saved (validation, constraint) is logged and left as it was. */
    private boolean saveOrSkip(UamUser user) {
        try {
            userRepository.save(user);
            return true;
        } catch (RuntimeException ex) {
            Throwable cause = ex;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            log.warn("Skipped placing account '{}' in the platform organization: {}", user.getUserName(), cause.getMessage());
            return false;
        }
    }

    private void adoptUnownedUsers(Tenant tenant) {
        int adopted = 0;
        int pageNumber = 0;
        Page<UamUser> page;
        do {
            page = userRepository.findAll(PageRequest.of(pageNumber++, DefaultOrganizationConstants.ADOPT_PAGE_SIZE));
            for (UamUser user : page.getContent()) {
                if (!StringUtils.hasText(user.getTenantId())) {
                    user.setTenantId(tenant.getId());
                    if (saveOrSkip(user)) {
                        adopted++;
                    }
                }
            }
        } while (page.hasNext());
        if (adopted > 0) {
            log.info("Gave {} accounts with no organization to the platform organization {}", adopted, tenant.getCode());
        }
    }
}
