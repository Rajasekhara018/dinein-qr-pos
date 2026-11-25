package com.heuristiq.identity.application.access;

import com.heuristiq.identity.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.heuristiq.phone.validation.ValidPhone;

@Schema(description = "Finds the account for this person, or creates it, and grants it access to an application. Called by the onboarding service when a submission is approved.")
public record ProvisionApplicationUserRequest(
        @Schema(description = "Display name, used only when a new account is created.", example = "Asha Rao") @NotBlank String name,
        @Schema(description = "Phone number in international (E.164, e.g. +919876543210) or Indian 10-digit form.", example = "9876543210")
        @NotBlank @ValidPhone String mobile,
        @Schema(description = "Contact email address.", example = "asha@example.com")
        @Email(message = "must be a valid email address") @NotBlank String email,
        @Schema(description = "Account type, used only when a new account is created.") @NotNull UamEnums.UserType userType,
        @Schema(description = "Code of the registered application to grant.", example = "PASUPU_KUMKUMA_MATRIMONY_APP") @NotBlank String applicationCode,
        @Schema(description = "Optional id of the organization the person is being onboarded into: they are made an active member of it (the same account can belong to several organizations). It must exist (else 404), be ACTIVE and hold an active subscription to the application (else 409). Omitted: the account's own organization, or the default one. Accepted only because this request comes from a trusted internal service.")
        @jakarta.validation.constraints.Size(max = 255) String tenantId
) {
}
