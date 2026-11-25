package com.heuristiq.identity.dto;

import com.heuristiq.identity.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requests an OTP be sent to the given email address.")
public record IdentityEmailOtpRequest(
        @Schema(description = "Destination email address.", example = "jane.doe@example.com")
        @Email(message = "must be a valid email address") @NotBlank String email,
        @Schema(description = "The type of user account this OTP request applies to.") @NotNull UamEnums.UserType userType
) {
}
