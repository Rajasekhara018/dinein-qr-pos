package com.heuristiq.identity.mfa;

import java.util.List;

public interface UserMfaServiceUseCase {

    record ListByUserInput(String userId) {}
    record ListByUserOutput(List<UserMfa> mfas) {}
    ListByUserOutput listByUser(ListByUserInput input);

    record ListEnabledByUserInput(String userId) {}
    record ListEnabledByUserOutput(List<UserMfa> mfas) {}
    ListEnabledByUserOutput listEnabledByUser(ListEnabledByUserInput input);

    record GetInput(String id) {}
    record GetOutput(UserMfa mfa) {}
    GetOutput get(GetInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);

    record DisableInput(String id) {}
    record DisableOutput(UserMfa mfa) {}
    DisableOutput disable(DisableInput input);

    /** Pure check — does not mutate the record. Used both by enrollment verification and login. */
    record VerifyCodeInput(UserMfa mfa, String code) {}
    record VerifyCodeOutput(boolean verified) {}
    VerifyCodeOutput verifyCode(VerifyCodeInput input);

    /** Step 1 of enabling 2FA: generates a secret + QR code and stores an unverified, disabled row. */
    record EnrollTotpInput(String userId) {}
    record EnrollTotpOutput(MfaEnrollTotpResponse response) {}
    EnrollTotpOutput enrollTotp(EnrollTotpInput input);

    /** Step 2: user enters a code generated from the QR they just scanned; activates the method. */
    record VerifyEnrollmentInput(String id, String code) {}
    record VerifyEnrollmentOutput(UserMfa mfa) {}
    VerifyEnrollmentOutput verifyEnrollment(VerifyEnrollmentInput input);
}
