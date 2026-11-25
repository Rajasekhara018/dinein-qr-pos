package com.heuristiq.identity.organization.admin;

import com.heuristiq.phone.validation.ValidPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "The person who administers a new organization. An account that already exists (same mobile or email) is reused.")
public record CreateOrganizationAdminRequest(
        @Schema(description = "Full name, used only when a new account is created.", example = "Asha Rao") @NotBlank String name,
        @Schema(description = "Mobile number in international (E.164) or Indian 10-digit form.", example = "9876543210")
        @NotBlank @ValidPhone String mobile,
        @Schema(description = "Email address the administrator signs in with.", example = "asha@acme.com")
        @NotBlank @Email(message = "must be a valid email address") String email
) {
}
