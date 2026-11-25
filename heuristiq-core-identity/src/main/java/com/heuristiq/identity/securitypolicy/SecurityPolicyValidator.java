package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.helper.AccountPolicy;
import com.heuristiq.identity.helper.LoginPolicy;
import com.heuristiq.identity.helper.PasswordPolicy;
import com.heuristiq.identity.helper.SessionPolicy;

/**
 * Range checks of a security policy, so a form cannot save values that lock everyone out (no sign-in attempts allowed,
 * sessions that end at once) or that cannot be met (a minimum password length above the maximum). Pure and stateless; a
 * section that is absent is not checked, and a field that is absent keeps its default.
 */
public final class SecurityPolicyValidator {

    private SecurityPolicyValidator() {
    }

    /** @throws UamException naming the first value that is out of range */
    public static void validate(UpsertSecurityPolicyRequest request) {
        password(request.passwordPolicy());
        login(request.loginPolicy());
        session(request.sessionPolicy());
        account(request.accountPolicy());
        if (request.effectiveFrom() != null && request.effectiveUntil() != null
                && !request.effectiveFrom().isBefore(request.effectiveUntil())) {
            throw new UamException("Policy end must be after its start");
        }
    }

    private static void password(PasswordPolicy policy) {
        if (policy == null) {
            return;
        }
        Integer min = policy.getMinimumLength();
        Integer max = policy.getMaximumLength();
        if (min != null) {
            requireBetween("Minimum password length", min, SecurityPolicyConstants.MIN_PASSWORD_LENGTH_FLOOR, SecurityPolicyConstants.PASSWORD_LENGTH_CEILING);
        }
        if (max != null) requireBetween("Maximum password length", max,
                SecurityPolicyConstants.MIN_PASSWORD_LENGTH_FLOOR, SecurityPolicyConstants.PASSWORD_LENGTH_CEILING);
        if (min != null && max != null && min > max) {
            throw new UamException("Minimum password length (" + min + ") cannot be more than the maximum (" + max + ")");
        }
        requireNotNegative("Password history count", policy.getPasswordHistoryCount());
        if (policy.getPasswordHistoryCount() != null && policy.getPasswordHistoryCount() > 100)
            throw new UamException("Password history count cannot exceed 100");
        requireNotNegative("Password expiry days", policy.getExpiryDays());
        requireNotNegative("Minimum password age days", policy.getMinimumAgeDays());
    }

    private static void login(LoginPolicy policy) {
        if (policy == null) {
            return;
        }
        if (policy.getMaxFailedAttempts() != null) {
            requireBetween("Maximum failed attempts", policy.getMaxFailedAttempts(),
                    SecurityPolicyConstants.MAX_FAILED_ATTEMPTS_MIN, SecurityPolicyConstants.MAX_FAILED_ATTEMPTS_MAX);
        }
        requireNotNegative("Lock duration minutes", policy.getLockDurationMinutes());
        requireNotNegative("Captcha after attempts", policy.getCaptchaAfterAttempts());
        requireNotNegative("Maximum concurrent sessions", policy.getMaxConcurrentSessions());
    }

    private static void session(SessionPolicy policy) {
        if (policy == null) {
            return;
        }
        requireAtLeast("Idle timeout minutes", policy.getIdleTimeoutMinutes(), SecurityPolicyConstants.MIN_SESSION_TIMEOUT_MINUTES);
        requireNotNegative("Absolute timeout minutes", policy.getAbsoluteTimeoutMinutes());
    }

    private static void account(AccountPolicy policy) {
        if (policy == null) {
            return;
        }
        requireNotNegative("Auto unlock after minutes", policy.getAutoUnlockAfterMinutes());
    }

    private static void requireBetween(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new UamException(field + " must be between " + min + " and " + max);
        }
    }

    private static void requireAtLeast(String field, Integer value, int min) {
        if (value != null && value < min) {
            throw new UamException(field + " must be at least " + min);
        }
    }

    private static void requireNotNegative(String field, Integer value) {
        if (value != null && value < 0) {
            throw new UamException(field + " cannot be negative");
        }
    }
}
