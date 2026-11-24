package com.heuristiq.uam.registration;

import com.heuristiq.uam.dto.IdentityRegisterRequest;
import com.heuristiq.uam.dto.RegistrationResponse;

/**
 * Generic registration for every user type (CUSTOMER/DRIVER/VENDOR/...) -- one unified
 * endpoint instead of a copy-pasted register* method + controller per type. Kept out of
 * {@link com.heuristiq.uam.otp.UamOtpAuthServiceUseCase}: registration doesn't verify any OTP, so
 * bundling it into the OTP-auth service was a Single-Responsibility mismatch (two unrelated
 * reasons to change one class).
 */
public interface UserRegistrationServiceUseCase {

    record RegisterInput(IdentityRegisterRequest request, boolean administrativeProvisioning) {
        public RegisterInput(IdentityRegisterRequest request) { this(request, false); }
    }
    record RegisterOutput(RegistrationResponse response) {}
    RegisterOutput register(RegisterInput input);
}
