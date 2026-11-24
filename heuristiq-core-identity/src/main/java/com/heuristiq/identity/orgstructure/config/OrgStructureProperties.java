package com.heuristiq.uam.orgstructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the organization-structure feature.
 *
 * @param defaultTenantId optional fixed id of the organization used for internal work that has no signed-in person; when blank
 *                        (the default) the organization with the platform code ({@code railcab.uam.platform-org.code}) is used
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.org-structure")
public record OrgStructureProperties(@DefaultValue("") String defaultTenantId) {
}
