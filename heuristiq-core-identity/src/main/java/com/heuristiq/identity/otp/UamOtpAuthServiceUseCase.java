package com.heuristiq.identity.otp;

import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.dto.LoginResponse;
import com.heuristiq.identity.dto.LoginResult;
import com.heuristiq.identity.dto.OtpSentResponse;
import com.heuristiq.identity.enums.UamEnums;

public interface UamOtpAuthServiceUseCase {

    record SendPhoneOtpInput(String phone, UamEnums.UserType userType) {}
    record SendPhoneOtpOutput(OtpSentResponse response) {}
    SendPhoneOtpOutput sendPhoneOtp(SendPhoneOtpInput input);

    /**
     * A successful OTP verification is a full login — it goes through
     * {@link com.heuristiq.identity.auth.LoginSessionService#issue} exactly like local/Google login does,
     * so it gets a real session + rotating refresh token (LoginResponse.refreshToken) too, not just
     * a bare access token. Response type was AppLoginResponse (no refreshToken) before this — that
     * record has been removed since nothing else used it.
     */
    record VerifyPhoneOtpInput(String phone, String otp, UamEnums.UserType userType, String totpCode) {
        public VerifyPhoneOtpInput(String phone, String otp, UamEnums.UserType userType) {
            this(phone, otp, userType, null);
        }
    }
    /** A {@link LoginResponse}, or a {@link com.heuristiq.identity.dto.MfaChallengeRequiredResponse} when the user has MFA enrolled and sent no totpCode. */
    record VerifyPhoneOtpOutput(LoginResult response) {}
    VerifyPhoneOtpOutput verifyPhoneOtp(VerifyPhoneOtpInput input);

    record SendEmailOtpInput(String email, UamEnums.UserType userType) {}
    record SendEmailOtpOutput(OtpSentResponse response) {}
    SendEmailOtpOutput sendEmailOtp(SendEmailOtpInput input);

    record VerifyEmailOtpInput(String email, String otp, UamEnums.UserType userType, String totpCode) {
        public VerifyEmailOtpInput(String email, String otp, UamEnums.UserType userType) {
            this(email, otp, userType, null);
        }
    }
    /** A {@link LoginResponse}, or a {@link com.heuristiq.identity.dto.MfaChallengeRequiredResponse} when the user has MFA enrolled and sent no totpCode. */
    record VerifyEmailOtpOutput(LoginResult response) {}
    VerifyEmailOtpOutput verifyEmailOtp(VerifyEmailOtpInput input);

    record ResetPasswordInput(String email, String otp, String newPassword, UamEnums.UserType userType) {}
    record ResetPasswordOutput() {}
    ResetPasswordOutput resetPassword(ResetPasswordInput input);

    record LoginPayloadInput(UamUser user, boolean mfaVerified) {
        public LoginPayloadInput(UamUser user) {
            this(user, false);
        }
    }
    record LoginPayloadOutput(LoginResponse response) {}
    LoginPayloadOutput loginPayload(LoginPayloadInput input);
}
