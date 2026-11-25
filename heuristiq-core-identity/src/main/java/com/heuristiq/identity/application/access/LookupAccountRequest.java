package com.heuristiq.identity.application.access;

import com.heuristiq.phone.validation.ValidPhone;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Asks whether an account already exists for this phone number and email, and how it matches. Called by the onboarding service before it registers a new account.")
public record LookupAccountRequest(
        @Schema(description = "Phone number in international (E.164, e.g. +919876543210) or Indian 10-digit form.", example = "9876543210")
        @NotBlank @ValidPhone String mobile,
        @Schema(description = "Contact email address; may be left out.", example = "asha@example.com") String email
) {
}
