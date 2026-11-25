package com.heuristiq.identity.lockout;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.lockout.repository.UserLoginSecurityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserLoginSecurityService implements UserLoginSecurityServiceUseCase {
    private final UserLoginSecurityRepository loginSecurityRepository;

    public UserLoginSecurityService(UserLoginSecurityRepository loginSecurityRepository) {
        this.loginSecurityRepository = loginSecurityRepository;
    }

    public GetByUserOutput getByUser(GetByUserInput input) {
        UserLoginSecurity security = loginSecurityRepository.findByUserId(input.userId())
                .orElseThrow(() -> UamException.notFound("Login security record not found"));
        return new GetByUserOutput(security);
    }

    public UnlockOutput unlock(UnlockInput input) {
        UserLoginSecurity security = getByUser(new GetByUserInput(input.userId())).security();
        security.setAccountLocked(false);
        security.setLockReason(null);
        security.setLockedAt(null);
        security.setUnlockAt(null);
        security.setFailedLoginAttempts(0);
        security.setUpdatedAt(LocalDateTime.now());
        return new UnlockOutput(loginSecurityRepository.save(security));
    }

    public ResetFailedAttemptsOutput resetFailedAttempts(ResetFailedAttemptsInput input) {
        UserLoginSecurity security = getByUser(new GetByUserInput(input.userId())).security();
        security.setFailedLoginAttempts(0);
        security.setUpdatedAt(LocalDateTime.now());
        return new ResetFailedAttemptsOutput(loginSecurityRepository.save(security));
    }

    // ── Called from AuthService.login() on every attempt so this record reflects real logins ──
    // instead of only what an admin manually seeded. The actual lockout decision still lives in
    // LoginAttemptService (keyed by username, enforced before password check); `locked` here just
    // mirrors that decision onto the per-user record the self-service screen reads.

    public RecordSuccessOutput recordSuccess(RecordSuccessInput input) {
        String userId = input.userId();
        String ip = input.ip();
        UserLoginSecurity security = loginSecurityRepository.findByUserId(userId).orElseGet(() -> newRecord(userId));
        LocalDateTime now = LocalDateTime.now();
        security.setPreviousSuccessfulLoginAt(security.getLastSuccessfulLoginAt());
        security.setLastSuccessfulLoginAt(now);
        security.setLastLoginIp(ip);
        security.setSuccessfulLoginCount(orZero(security.getSuccessfulLoginCount()) + 1);
        security.setFailedLoginAttempts(0);
        security.setAccountLocked(false);
        security.setFirstLogin(false);
        security.setUpdatedAt(now);
        return new RecordSuccessOutput(loginSecurityRepository.save(security));
    }

    public RecordFailureOutput recordFailure(RecordFailureInput input) {
        String userId = input.userId();
        String ip = input.ip();
        boolean locked = input.locked();
        UserLoginSecurity security = loginSecurityRepository.findByUserId(userId).orElseGet(() -> newRecord(userId));
        LocalDateTime now = LocalDateTime.now();
        security.setLastFailedLoginAt(now);
        security.setLastFailedLoginIp(ip);
        security.setFailedLoginAttempts(orZero(security.getFailedLoginAttempts()) + 1);
        security.setFailedLoginCount(orZero(security.getFailedLoginCount()) + 1);
        if (locked) {
            security.setAccountLocked(true);
            security.setLockReason("Too many failed login attempts");
            security.setLockedAt(now);
        }
        security.setUpdatedAt(now);
        return new RecordFailureOutput(loginSecurityRepository.save(security));
    }

    private UserLoginSecurity newRecord(String userId) {
        return UserLoginSecurity.builder().userId(userId).createdAt(LocalDateTime.now()).build();
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
