package com.heuristiq.identity.application;

import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import com.heuristiq.identity.enums.UamEnums.AuthenticationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Create/update payload for a client application registered with this UAM service.")
public record UpsertApplicationRequest(
        @Schema(description = "Application code: one of the application catalogue codes; cannot change after creation.", example = "BESTRIDE_RIDER_APP") @NotBlank @Size(max = 100, message = "must be at most 100 characters") String code,
        @Schema(description = "Display name.", example = "Customer App") @Size(max = 200, message = "must be at most 200 characters") String name,
        @Schema(description = "Free-text description, up to 1000 characters.") @Size(max = 1000, message = "must be at most 1000 characters") String description,
        @Schema(description = "Current released version.", example = "1.4.2") @Size(max = 255, message = "must be at most 255 characters") String appVersion,
        @Schema(description = "Base URL the application is served from.", example = "https://app.railcab.com") @Size(max = 255, message = "must be at most 255 characters") String baseUrl,
        @Schema(description = "Landing page path shown after login.", example = "/dashboard") @Size(max = 255, message = "must be at most 255 characters") String landingPage,
        @Schema(description = "Team responsible for this application.", example = "Platform Team") @Size(max = 255, message = "must be at most 255 characters") String ownerTeam,
        @Schema(description = "Support contact email address.", example = "support@railcab.com")
        @Email(message = "must be a valid email address") @Size(max = 255, message = "must be at most 255 characters") String supportEmail,
        @Schema(description = "Type of application.") ApplicationType type,
        @Schema(description = "Authentication mode this application uses.") AuthenticationMode authenticationMode,
        @Schema(description = "Whether SSO is enabled for this application.") Boolean ssoEnabled,
        @Schema(description = "Whether MFA is required for this application.") Boolean mfaRequired,
        @Schema(description = "Sort order among applications, must not be negative.", example = "1")
        @PositiveOrZero(message = "must not be negative") Integer displayOrder,
        @Schema(description = "Switches the application on or off for the session organization only (left out = unchanged).") Boolean active
) {
    /** Without the organization's on/off switch (it stays as it is). */
    public UpsertApplicationRequest(String code, String name, String description, String appVersion, String baseUrl, String landingPage,
                                    String ownerTeam, String supportEmail, ApplicationType type, AuthenticationMode authenticationMode,
                                    Boolean ssoEnabled, Boolean mfaRequired, Integer displayOrder) {
        this(code, name, description, appVersion, baseUrl, landingPage, ownerTeam, supportEmail, type, authenticationMode,
                ssoEnabled, mfaRequired, displayOrder, null);
    }
}
