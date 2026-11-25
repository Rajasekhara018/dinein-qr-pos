package com.heuristiq.identity.dto;

import com.heuristiq.identity.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.heuristiq.phone.validation.ValidPhone;

@Schema(description = "Requests an OTP be sent to the given mobile number via SMS.")
public record IdentityPhoneOtpRequest(
        @Schema(description = "Destination phone number in international (E.164, e.g. +919876543210) or Indian 10-digit form.", example = "9876543210")
        @NotBlank @ValidPhone String phone,
        @Schema(description = "The type of user account this OTP request applies to.") @NotNull UamEnums.UserType userType
) {
}
