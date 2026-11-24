package com.heuristiq.uam.organization;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The organization that owns the platform itself ({@code railcab.uam.platform-org.*}): the one the bootstrap super admin
 * and, optionally, every account created before organizations existed belong to. It is only used when seeding and when
 * placing an account that has no membership yet; a signed-in request always takes its organization from the token.
 *
 * @param code               the organization's code (looked up case-insensitively, created if it does not exist)
 * @param name               its name when it is created
 * @param adoptUnownedUsers  true gives every account with no organization to this one at start-up (a one-off migration
 *                           switch, off by default so no account is ever moved without being asked to)
 */
@ConfigurationProperties(prefix = "railcab.uam.platform-org")
public record PlatformOrganizationProperties(
        @DefaultValue("DEFAULT") @NotBlank String code,
        @DefaultValue("Default organization") @NotBlank String name,
        @DefaultValue("false") boolean adoptUnownedUsers) {

    /** The built-in values: the organization {@code DEFAULT}, nothing adopted. */
    public static PlatformOrganizationProperties defaults() {
        return new PlatformOrganizationProperties("DEFAULT", "Default organization", false);
    }
}
