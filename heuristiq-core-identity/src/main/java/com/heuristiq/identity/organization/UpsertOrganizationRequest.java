package com.heuristiq.identity.organization;

import com.heuristiq.identity.enums.UamEnums.LanguageEnum;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Create/update payload for a tenant organization.")
public record UpsertOrganizationRequest(
        @Schema(description = "Unique organization code.", example = "ACME_CORP") @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{2,30}$", message = "must be 2-30 letters, digits or underscores") String code,
        @Schema(description = "Display name.", example = "Acme Corp") @NotBlank @Size(max = 100) String name,
        @Schema(description = "Free-text description.") @Size(max = 500) String description,
        @Schema(description = "Type of tenant organization.") TenantType type,
        @Schema(description = "Current tenant status.") TenantStatus status,
        @Schema(description = "Support contact email address.", example = "support@acme.com")
        @Email(message = "must be a valid email address") String supportEmail,
        // Organization support line: may be a landline or toll-free number (e.g. 1800...), so it is
        // intentionally NOT constrained to a mobile-number rule (no @ValidPhone) for any country.
        @Schema(description = "Support contact phone number.", example = "1800123456") String supportPhone,
        @Schema(description = "How much of the organization people see: ORGANIZATION (all, the default) or DEPARTMENT (own department and below).", example = "DEPARTMENT")
        @Pattern(regexp = "^(ORGANIZATION|DEPARTMENT)$", message = "must be ORGANIZATION or DEPARTMENT") String dataScope,
        @Schema(description = "Maximum number of users allowed for this tenant, must be at least 1.", example = "100")
        @Positive(message = "must be greater than zero") Integer maxUsers,
        @Schema(description = "Maximum number of applications allowed for this tenant, must be at least 1.", example = "10")
        @Positive(message = "must be greater than zero") Integer maxApplications,
        @Schema(description = "Offer this organization on the public self-signup list for the applications it subscribes to. Off by default; leaving it out on an update keeps the current choice.", example = "false")
        Boolean openForSelfSignup,
        @Schema(description = "Currency the organization's amounts are in (ISO 4217). Leaving it out on an update keeps the current one.", example = "INR")
        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "must be a three-letter ISO currency code") String currency,
        @Schema(description = "The organization's home country (ISO 3166 alpha-2): phone numbers and places start there.", example = "IN")
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "must be a two-letter ISO country code") String country,
        @Schema(description = "The organization's time zone (IANA): its days, reports and schedules follow it.", example = "Asia/Kolkata")
        @Size(max = 64) String timeZone,
        @Schema(description = "The language the organization's people start in until they choose their own.", example = "EN")
        LanguageEnum defaultLanguage
) {

    /** A request without regional settings (they are left as they are on an update). */
    public UpsertOrganizationRequest(String code, String name, String description, TenantType type, TenantStatus status, String supportEmail,
                                     String supportPhone, String dataScope, Integer maxUsers, Integer maxApplications, Boolean openForSelfSignup) {
        this(code, name, description, type, status, supportEmail, supportPhone, dataScope, maxUsers, maxApplications, openForSelfSignup,
                null, null, null, null);
    }
}
