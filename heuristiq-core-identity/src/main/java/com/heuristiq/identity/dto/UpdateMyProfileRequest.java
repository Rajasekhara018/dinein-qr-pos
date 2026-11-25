package com.heuristiq.identity.dto;

import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.helper.RUpload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import com.heuristiq.phone.validation.ValidPhone;

/**
 * Self-service profile update — deliberately excludes username/roles/status/tenantId/userType,
 * which only {@code UserController}'s SUPER_ADMIN-gated {@code UpsertUserRequest} may change.
 */
@Schema(description = "Partial self-service profile update for the caller's own account. Any field left null "
        + "keeps its current value -- only non-null/non-blank fields are applied.")
public record UpdateMyProfileRequest(
        @Schema(description = "Display name.", example = "Jane Doe") String fullName,
        @Schema(description = "Contact email address.", example = "jane.doe@example.com")
        @Email(message = "must be a valid email address") String email,
        @Schema(description = "Contact mobile number.", example = "9876543210")
        @ValidPhone String phoneNumber,
        @Schema(description = "IANA timezone id.", example = "Asia/Kolkata") String timezone,
        @Schema(description = "Preferred UI language.") UamEnums.LanguageEnum preferredLanguage,
        @Schema(description = "Gender.") UamEnums.Gender gender,
        @Schema(description = "Profile image upload reference.") RUpload profileImage
) {
}
