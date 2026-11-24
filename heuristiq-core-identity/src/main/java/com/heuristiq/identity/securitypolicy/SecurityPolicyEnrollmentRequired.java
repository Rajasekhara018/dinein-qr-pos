package com.heuristiq.uam.securitypolicy;

/** Returned only after primary authentication succeeds, before a full session is created. */
public class SecurityPolicyEnrollmentRequired extends RuntimeException {
    private final SecurityPolicyEnrollmentService.Challenge challenge;
    public SecurityPolicyEnrollmentRequired(SecurityPolicyEnrollmentService.Challenge challenge) {
        super("Authenticator enrollment is required"); this.challenge = challenge;
    }
    public SecurityPolicyEnrollmentService.Challenge challenge() { return challenge; }
}
