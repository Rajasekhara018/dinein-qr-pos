package com.heuristiq.uam.dto;

import com.heuristiq.uam.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.heuristiq.phone.validation.ValidPhone;
import com.heuristiq.uam.dto.validation.ValidPersonName;

/**
 * Generic registration request for any UserType beyond CUSTOMER/DRIVER (which keep their own
 * dedicated *RegisterRequest DTOs since existing clients depend on those exact shapes).
 * Identity-only fields -- business/KYC data (e.g. a vendor's GST/bank details) lives on the
 * owning service's own record, not on UamUser (mirrors DriverRegisterRequest's KYC fields,
 * which UamOtpAuthService.createUser already drops).
 */
@Schema(description = "Registers a new identity (any UserType besides CUSTOMER/DRIVER, which have their own dedicated registration DTOs).")
public record IdentityRegisterRequest(
        @Schema(description = "Display name: letters (any script), spaces, '.', apostrophe and hyphen; starts with a letter; 2 to 80 characters.", example = "Jane Doe")
        @NotBlank @ValidPersonName String name,
        @Schema(description = "Phone number in international (E.164, e.g. +919876543210) or Indian 10-digit form.", example = "9876543210")
        @NotBlank @ValidPhone String mobile,
        @Schema(description = "Contact email address.", example = "jane.doe@example.com")
        @Email(message = "must be a valid email address") @NotBlank String email,
        @Schema(description = "City of residence.", example = "Bengaluru") String city,
        @Schema(description = "The type of user account being registered.") @NotNull UamEnums.UserType userType
) {
}
