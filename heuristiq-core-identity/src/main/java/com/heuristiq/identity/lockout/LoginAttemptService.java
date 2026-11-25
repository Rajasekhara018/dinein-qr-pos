package com.heuristiq.identity.lockout;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class LoginAttemptService implements LoginAttemptServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.identity.securitypolicy.SecurityPolicyEnforcer policyEnforcer;
    private final LoginAttemptStore store;
    private final MeterRegistry meterRegistry;
    private final int maxFailures;
    private final int lockoutMinutes;

    public LoginAttemptService(LoginAttemptStore store,
                               MeterRegistry meterRegistry,
                               @Value("${railcab.uam.auth.max-failed-attempts:5}") int maxFailures,
                               @Value("${railcab.uam.auth.lockout-minutes:15}") int lockoutMinutes) {
        this.store = store;
        this.meterRegistry = meterRegistry;
        this.maxFailures = Math.max(maxFailures, 1);
        this.lockoutMinutes = Math.max(lockoutMinutes, 1);
    }

    public IsLockedOutput isLocked(IsLockedInput input) {
        String username = input.username();
        String key = key(username);
        LoginAttemptState state = store.find(key).orElse(null);
        if (state == null) {
            return new IsLockedOutput(false);
        }
        Instant now = Instant.now();
        if (state.expiresAt().isBefore(now)) {
            store.delete(key);
            return new IsLockedOutput(false);
        }
        if (state.lockedUntil() != null && state.lockedUntil().isAfter(now)) {
            meterRegistry.counter("uam.auth.lockout.active").increment();
            return new IsLockedOutput(true);
        }
        return new IsLockedOutput(false);
    }

    public RecordSuccessOutput recordSuccess(RecordSuccessInput input) {
        String username = input.username();
        store.delete(key(username));
        meterRegistry.counter("uam.auth.login.attempts", "result", "success").increment();
        return new RecordSuccessOutput();
    }

    // Admin-triggered unlock (UserLoginSecurityController) — clears the actual lockout gate,
    // not just the UserLoginSecurity mirror record which has no effect on isLocked().
    public UnlockOutput unlock(UnlockInput input) {
        String username = input.username();
        store.delete(key(username));
        meterRegistry.counter("uam.auth.lockout.admin_unlock").increment();
        return new UnlockOutput();
    }

    public RecordFailureOutput recordFailure(RecordFailureInput input) {
        String username = input.username();
        String key = key(username);
        Instant now = Instant.now();
        int policyFailures = maxFailures;
        int policyMinutes = lockoutMinutes;
        if (policyEnforcer != null) {
            var policy = policyEnforcer.forUsername(username);
            policyFailures = Math.min(maxFailures, policy.number("loginPolicy.maxFailedAttempts"));
            policyMinutes = Math.max(lockoutMinutes, policy.number("loginPolicy.lockDurationMinutes"));
        }
        LoginAttemptState current = store.find(key).orElse(null);
        int failures = current == null || current.expiresAt().isBefore(now)
                ? 1
                : current.failures() + 1;
        Instant lockedUntil = failures >= policyFailures
                ? now.plusSeconds(policyMinutes * 60L)
                : null;
        Instant expiresAt = lockedUntil == null ? now.plusSeconds(policyMinutes * 60L) : lockedUntil;
        store.save(new LoginAttemptState(key, failures, lockedUntil, expiresAt));
        meterRegistry.counter("uam.auth.login.attempts", "result", "failure").increment();
        if (lockedUntil != null) {
            meterRegistry.counter("uam.auth.lockouts").increment();
        }
        return new RecordFailureOutput();
    }

    private String key(String username) {
        return StringUtils.hasText(username) ? username.trim().toLowerCase() : "";
    }
}
