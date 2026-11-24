package com.heuristiq.uam.organization.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;

/**
 * Once the service is up, brings every organization's existing administrator role up to date: administrators set up
 * before the role covered every application keep working without being set up again. Safe to repeat, and a failure for
 * one organization is logged and never stops the service or the other organizations.
 */
@Component
public class OrganizationAdminBackfill {
    private static final Logger log = LoggerFactory.getLogger(OrganizationAdminBackfill.class);

    private final TenantRepository tenants;
    private final OrganizationAdminServiceUseCase admins;

    public OrganizationAdminBackfill(TenantRepository tenants, OrganizationAdminServiceUseCase admins) {
        this.tenants = tenants;
        this.admins = admins;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshAll() {
        int refreshed = 0;
        int pageNumber = 0;
        Page<Tenant> page;
        do {
            page = tenants.findAll(PageRequest.of(pageNumber++, OrganizationAdminConstants.BACKFILL_PAGE_SIZE));
            for (Tenant tenant : page.getContent()) {
                try {
                    if (admins.refreshRole(tenant)) {
                        refreshed++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not refresh the administrator role of organization {}: {}", tenant.getCode(), e.getMessage());
                }
            }
        } while (page.hasNext());
        if (refreshed > 0) {
            log.info("Refreshed the administrator role of {} organizations", refreshed);
        }
    }
}
