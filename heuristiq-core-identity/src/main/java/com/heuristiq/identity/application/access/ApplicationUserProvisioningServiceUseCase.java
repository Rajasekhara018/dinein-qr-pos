package com.heuristiq.uam.application.access;

import java.util.List;

public interface ApplicationUserProvisioningServiceUseCase {

    record ProvisionInput(ProvisionApplicationUserRequest request) {}

    /**
     * @param created           true when this call created the account, false when it already existed and only the grant was added
     * @param applicationCodes  every application the account can now sign in to
     * @param temporaryPassword the one-time password set on an account this call created (the member must change it at
     *                          first sign-in); null for an account that already existed, which keeps its own credentials.
     *                          Only the internal caller that tells the member ever sees it; it is never stored in clear.
     */
    record ProvisionOutput(String userId, boolean created, List<String> applicationCodes, String temporaryPassword) {}

    /**
     * Finds the account by mobile or email, or creates it, then grants it access to the application.
     * Idempotent, so a caller may safely repeat it after a failure part-way through. An existing
     * account keeps its credentials, roles and name untouched.
     */
    ProvisionOutput provision(ProvisionInput input);

    record LookupInput(String mobile, String email) {}

    /**
     * What is known about the account that already uses the phone number or the email.
     *
     * @param found       whether any account uses the phone number or the email
     * @param userId      the account, or null when none was found or the phone number and the email belong to two different accounts
     *                    (then {@code found} is true and nothing may be reused)
     * @param mobileMatch the account uses this phone number
     * @param emailMatch  the account uses this email
     * @param active      the account is ACTIVE
     * @param roles       the account's roles, upper case
     */
    record LookupOutput(boolean found, String userId, boolean mobileMatch, boolean emailMatch, boolean active, List<String> roles) {}

    /**
     * Looks for an account by phone number and email without creating or changing anything. Used by a caller that is about to
     * register a person and wants to reuse an account that is certainly theirs (both identifiers match) instead of failing.
     */
    LookupOutput lookup(LookupInput input);

    record IssueTemporaryPasswordInput(String userId) {}

    /**
     * @param temporaryPassword the one-time password just set (the member must change it at first sign-in), or null when the
     *                          account already has a password of its own and was left untouched.
     */
    record IssueTemporaryPasswordOutput(String userId, String temporaryPassword) {}

    /**
     * Gives an active customer account that has no password yet (one that signed up with a one-time code) a random temporary
     * password, so it can be shared once the account is activated. An account that already has a password is never changed,
     * and back-office accounts are refused.
     */
    IssueTemporaryPasswordOutput issueTemporaryPassword(IssueTemporaryPasswordInput input);
}
