package com.heuristiq.uam.organization.admin;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.role.repository.UamRoleRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps an organization's administrator role able to sign in to every application the organization has: when the
 * organization subscribes to an application, the role (if the organization has set one up) is given access to it too.
 */
@Component
@Slf4j
public class OrganizationAdminAccess {

    private final UamRoleRepository roles;
    private final OrganizationAdminServiceUseCase service;

    public OrganizationAdminAccess(UamRoleRepository roles, OrganizationAdminServiceUseCase service) {
        this.roles = roles;
        this.service = service;
    }

    /** Gives the organization's administrator role access to a newly subscribed application. A failure is logged, never thrown. */
    public void onSubscribed(Tenant tenant, String applicationCode) {
        String code = tenant.getCode().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_")
                + OrganizationAdminConstants.ADMIN_ROLE_SUFFIX;
        try {
            if (roles.findByCodeIgnoreCase(code).filter(r -> tenant.getId().equals(r.getTenantId())).isPresent()) {
                service.grantAccess(code, applicationCode);
            }
        } catch (RuntimeException e) {
            log.warn("Could not give the {} role access to {}: {}", code, applicationCode, e.getMessage());
        }
    }
}
