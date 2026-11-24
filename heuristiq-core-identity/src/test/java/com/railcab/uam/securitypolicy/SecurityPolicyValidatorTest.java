package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.helper.AccountPolicy;
import com.heuristiq.uam.helper.LoginPolicy;
import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.helper.SessionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Range rules of {@link SecurityPolicyValidator}. */
class SecurityPolicyValidatorTest {

    private static UpsertSecurityPolicyRequest request(PasswordPolicy password, LoginPolicy login, SessionPolicy session, AccountPolicy account) {
        return new UpsertSecurityPolicyRequest(null, null, "Policy", null, password, login, session, null, null, account);
    }

    private static void refused(UpsertSecurityPolicyRequest request, String messagePart) {
        assertThatThrownBy(() -> SecurityPolicyValidator.validate(request)).isInstanceOf(UamException.class).hasMessageContaining(messagePart);
    }

    @Test
    void aPolicyWithNoSectionsOrNoFieldsIsAccepted() {
        assertThatCode(() -> SecurityPolicyValidator.validate(request(null, null, null, null))).doesNotThrowAnyException();
        assertThatCode(() -> SecurityPolicyValidator.validate(request(new PasswordPolicy(), new LoginPolicy(), new SessionPolicy(), new AccountPolicy())))
                .doesNotThrowAnyException();
    }

    @Test
    void passwordLengthsMustBeInRangeAndMinNotAboveMax() {
        refused(request(PasswordPolicy.builder().minimumLength(20).maximumLength(8).build(), null, null, null), "cannot be more than");
        refused(request(PasswordPolicy.builder().minimumLength(7).build(), null, null, null), "Minimum password length");
        refused(request(PasswordPolicy.builder().minimumLength(129).build(), null, null, null), "Minimum password length");
        refused(request(PasswordPolicy.builder().maximumLength(7).build(), null, null, null), "Maximum password length");
        refused(request(PasswordPolicy.builder().maximumLength(200).build(), null, null, null), "Maximum password length");
        refused(request(PasswordPolicy.builder().minimumLength(60).maximumLength(59).build(), null, null, null), "cannot be more than");
        assertThatCode(() -> SecurityPolicyValidator.validate(request(PasswordPolicy.builder().minimumLength(8).maximumLength(128).build(), null, null, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> SecurityPolicyValidator.validate(request(PasswordPolicy.builder().minimumLength(20).maximumLength(20).build(), null, null, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> SecurityPolicyValidator.validate(request(PasswordPolicy.builder().minimumLength(128).build(), null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void passwordAgesAndHistoryCannotBeNegative() {
        refused(request(PasswordPolicy.builder().passwordHistoryCount(-1).build(), null, null, null), "Password history count");
        refused(request(PasswordPolicy.builder().expiryDays(-1).build(), null, null, null), "Password expiry days");
        refused(request(PasswordPolicy.builder().minimumAgeDays(-1).build(), null, null, null), "Minimum password age");
        assertThatCode(() -> SecurityPolicyValidator.validate(request(
                PasswordPolicy.builder().passwordHistoryCount(0).expiryDays(0).minimumAgeDays(0).build(), null, null, null))).doesNotThrowAnyException();
    }

    @Test
    void failedAttemptsMustBeBetweenOneAndTwenty() {
        for (int bad : new int[] {-1, 0, 21}) {
            refused(request(null, LoginPolicy.builder().maxFailedAttempts(bad).build(), null, null), "Maximum failed attempts");
        }
        for (int ok : new int[] {1, 5, 20}) {
            assertThatCode(() -> SecurityPolicyValidator.validate(request(null, LoginPolicy.builder().maxFailedAttempts(ok).build(), null, null)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void lockDurationCaptchaAndConcurrentSessionsAreChecked() {
        refused(request(null, LoginPolicy.builder().lockDurationMinutes(-5).build(), null, null), "Lock duration");
        refused(request(null, LoginPolicy.builder().captchaAfterAttempts(-1).build(), null, null), "Captcha");
        refused(request(null, LoginPolicy.builder().maxConcurrentSessions(-1).build(), null, null), "concurrent sessions");
        assertThatCode(() -> SecurityPolicyValidator.validate(request(null,
                LoginPolicy.builder().lockDurationMinutes(0).captchaAfterAttempts(0).maxConcurrentSessions(0).build(), null, null))).doesNotThrowAnyException();
    }

    @Test
    void sessionTimeoutsMustBeAtLeastOneMinute() {
        refused(request(null, null, SessionPolicy.builder().idleTimeoutMinutes(0).build(), null), "Idle timeout");
        refused(request(null, null, SessionPolicy.builder().idleTimeoutMinutes(-1).build(), null), "Idle timeout");
        refused(request(null, null, SessionPolicy.builder().absoluteTimeoutMinutes(-1).build(), null), "Absolute timeout");
        assertThatCode(() -> SecurityPolicyValidator.validate(request(null, null,
                SessionPolicy.builder().idleTimeoutMinutes(1).absoluteTimeoutMinutes(0).build(), null))).doesNotThrowAnyException();
    }

    @Test
    void autoUnlockCannotBeNegative() {
        refused(request(null, null, null, AccountPolicy.builder().autoUnlockAfterMinutes(-1).build()), "Auto unlock");
        assertThatCode(() -> SecurityPolicyValidator.validate(request(null, null, null, AccountPolicy.builder().autoUnlockAfterMinutes(0).build())))
                .doesNotThrowAnyException();
    }
}
