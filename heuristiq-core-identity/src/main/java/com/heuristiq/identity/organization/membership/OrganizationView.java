package com.heuristiq.uam.organization.membership;

import com.heuristiq.uam.helper.IanaTimeZone;
import io.swagger.v3.oas.annotations.media.Schema;

/** An organization a person belongs to, for choosing which one to work in. */
public record OrganizationView(
        String tenantId,
        String code,
        String name,
        TenantMembershipStatus status,
        @Schema(description = "The organization's IANA timezone, or null when unset or not a valid zone. Display hint only.",
                nullable = true, example = "Asia/Kolkata") String timeZone,
        @Schema(description = "The organization's currency (ISO 4217), or null when unset: money fields start in it.", nullable = true, example = "INR")
        String currency,
        @Schema(description = "The organization's home country (ISO 3166 alpha-2), or null: phone numbers and places start there.", nullable = true, example = "IN")
        String country,
        @Schema(description = "The language the organization's people start in (EN, HI, TE ...), or null.", nullable = true, example = "EN")
        String defaultLanguage) {

    /** The view as it was before the timezone existed; {@code timeZone} is null. */
    public OrganizationView(String tenantId, String code, String name, TenantMembershipStatus status) {
        this(tenantId, code, name, status, null, null, null, null);
    }

    /** Builds a view whose stored zone is validated (an invalid stored value becomes null). */
    public static OrganizationView of(String tenantId, String code, String name, TenantMembershipStatus status, String storedTimeZone) {
        return new OrganizationView(tenantId, code, name, status, IanaTimeZone.validOrNull(storedTimeZone), null, null, null);
    }

    /** The view of an organization with its regional settings (the zone validated as above). */
    public static OrganizationView of(com.heuristiq.uam.organization.Tenant tenant, TenantMembershipStatus status) {
        return new OrganizationView(tenant.getId(), tenant.getCode(), tenant.getName(), status, IanaTimeZone.validOrNull(tenant.getTimeZone()),
                tenant.getCurrency(), tenant.getCountry(), tenant.getDefaultLanguage() == null ? null : tenant.getDefaultLanguage().name());
    }
}
