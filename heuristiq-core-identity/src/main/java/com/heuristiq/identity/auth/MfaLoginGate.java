package com.heuristiq.uam.auth;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The single MFA rule for login paths that do not use a password (OTP, Google): a user with an
 * enabled MFA method must present a valid authenticator code before a full session is issued --
 * exactly what {@link AuthService#login} requires after the password check. Users who never
 * enrolled keep the single-factor behaviour. Wrong codes count against the same login-attempt
 * lockout as password login.
 */
@Component
public class MfaLoginGate {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.SecurityPolicyEnforcer policyEnforcer;
    private final UserMfaServiceUseCase userMfaService;
    private final LoginAttemptServiceUseCase loginAttemptService;

    public MfaLoginGate(UserMfaServiceUseCase userMfaService, LoginAttemptServiceUseCase loginAttemptService) {
        this.userMfaService = userMfaService;
        this.loginAttemptService = loginAttemptService;
    }

    /** Picks the primary enabled method, else the first enabled one, else null (same choice password login makes). */
    public static UserMfa selectPrimary(List<UserMfa> enabledMfa) {
        return enabledMfa.stream().filter(m -> Boolean.TRUE.equals(m.getPrimary())).findFirst()
                .orElse(enabledMfa.stream().findFirst().orElse(null));
    }

    /** The MFA method {@code user} must complete before getting a full session, if any. */
    public Optional<UserMfa> requiredFor(UamUser user) {
        List<UserMfa> enabled = userMfaService.listEnabledByUser(
                new UserMfaServiceUseCase.ListEnabledByUserInput(user.getId())).mfas();
        List<UserMfa> allowed = enabled.stream().filter(mfa -> policyEnforcer == null || policyEnforcer.methodAllowed(user, mfa.getMfaType())).toList();
        if (!enabled.isEmpty() && allowed.isEmpty()) throw new UamException("No enrolled MFA method is allowed by the security policy");
        return Optional.ofNullable(selectPrimary(allowed));
    }

    /**
     * Verifies the authenticator code for {@code mfa}, enforcing the shared login lockout.
     *
     * @throws UamException when the account is locked out or the code is blank/wrong
     */
    public void verifyOrReject(UamUser user, UserMfa mfa, String totpCode) {
        String username = user.getUserName();
        if (loginAttemptService.isLocked(new LoginAttemptServiceUseCase.IsLockedInput(username)).locked()) {
            throw new UamException(AuthConstants.LOGIN_LOCKED_MESSAGE);
        }
        if (!userMfaService.verifyCode(new UserMfaServiceUseCase.VerifyCodeInput(mfa, totpCode)).verified()) {
            loginAttemptService.recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput(username));
            throw new UamException(AuthConstants.INVALID_AUTHENTICATOR_CODE_MESSAGE);
        }
        loginAttemptService.recordSuccess(new LoginAttemptServiceUseCase.RecordSuccessInput(username));
    }
}
