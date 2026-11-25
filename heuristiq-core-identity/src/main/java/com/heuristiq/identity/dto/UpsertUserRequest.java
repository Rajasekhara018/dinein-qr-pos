package com.heuristiq.identity.dto;

import com.heuristiq.identity.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.heuristiq.phone.validation.ValidPhone;

import java.util.List;

@Schema(description = "Create/update payload for a UAM user account. Used for both create (POST /users) "
        + "and update (PUT /users/{id}); on update, password/status may be left unset to keep current values "
        + "depending on service semantics.")
public record UpsertUserRequest(
        @Schema(description = "Unique login username.", example = "jane.doe") @NotBlank String username,
        @Schema(description = "Plaintext password, hashed server-side before storage. Required on create.") String password,
        @Schema(description = "Display name.", example = "Jane Doe") String fullName,
        @Schema(description = "The type of user account being managed.") UamEnums.UserType userType,
        @Schema(description = "Contact email address.", example = "jane.doe@example.com")
        @Email(message = "must be a valid email address") String email,
        @Schema(description = "Contact mobile number.", example = "9876543210")
        @ValidPhone String phoneNumber,
        @Schema(description = "Role codes to assign to this user.") List<String> roles,
        @Schema(description = "Account status.") UamEnums.UserStatus status
) {
}
