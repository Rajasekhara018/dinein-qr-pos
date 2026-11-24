package com.heuristiq.uam.lockout;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase.GetByUserInput;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase.RecordFailureInput;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase.RecordSuccessInput;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase.ResetFailedAttemptsInput;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase.UnlockInput;
import com.heuristiq.uam.lockout.repository.UserLoginSecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserLoginSecurityServiceTest {
    private UserLoginSecurityRepository repository;
    private UserLoginSecurityService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserLoginSecurityRepository.class);
        when(repository.save(any(UserLoginSecurity.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UserLoginSecurityService(repository);
    }

    @Test
    void getByUserReturnsRecordOrNotFound() {
        UserLoginSecurity existing = UserLoginSecurity.builder().userId("u1").build();
        when(repository.findByUserId("u1")).thenReturn(Optional.of(existing));
        when(repository.findByUserId("zz")).thenReturn(Optional.empty());

        assertThat(service.getByUser(new GetByUserInput("u1")).security()).isSameAs(existing);
        assertThatThrownBy(() -> service.getByUser(new GetByUserInput("zz")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unlockClearsEveryLockField() {
        UserLoginSecurity locked = UserLoginSecurity.builder().userId("u1").accountLocked(true)
                .lockReason("x").lockedAt(LocalDateTime.now()).unlockAt(LocalDateTime.now()).failedLoginAttempts(7).build();
        when(repository.findByUserId("u1")).thenReturn(Optional.of(locked));

        UserLoginSecurity out = service.unlock(new UnlockInput("u1")).security();

        assertThat(out.getAccountLocked()).isFalse();
        assertThat(out.getLockReason()).isNull();
        assertThat(out.getLockedAt()).isNull();
        assertThat(out.getUnlockAt()).isNull();
        assertThat(out.getFailedLoginAttempts()).isZero();
        assertThat(out.getUpdatedAt()).isNotNull();
    }

    @Test
    void unlockOfMissingRecordFails() {
        when(repository.findByUserId("u1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.unlock(new UnlockInput("u1"))).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.resetFailedAttempts(new ResetFailedAttemptsInput("u1")))
                .isInstanceOf(UamException.class);
    }

    @Test
    void resetFailedAttemptsZeroesCounterOnly() {
        UserLoginSecurity s = UserLoginSecurity.builder().userId("u1").failedLoginAttempts(4).accountLocked(true).build();
        when(repository.findByUserId("u1")).thenReturn(Optional.of(s));

        UserLoginSecurity out = service.resetFailedAttempts(new ResetFailedAttemptsInput("u1")).security();

        assertThat(out.getFailedLoginAttempts()).isZero();
        assertThat(out.getAccountLocked()).isTrue();
    }

    @Test
    void recordSuccessCreatesRecordAndShiftsPreviousLogin() {
        when(repository.findByUserId("new")).thenReturn(Optional.empty());

        UserLoginSecurity first = service.recordSuccess(new RecordSuccessInput("new", "10.0.0.1")).security();

        assertThat(first.getUserId()).isEqualTo("new");
        assertThat(first.getSuccessfulLoginCount()).isEqualTo(1L);
        assertThat(first.getLastLoginIp()).isEqualTo("10.0.0.1");
        assertThat(first.getFirstLogin()).isFalse();
        assertThat(first.getPreviousSuccessfulLoginAt()).isNull();

        when(repository.findByUserId("new")).thenReturn(Optional.of(first));
        LocalDateTime lastLogin = first.getLastSuccessfulLoginAt();
        UserLoginSecurity second = service.recordSuccess(new RecordSuccessInput("new", "10.0.0.2")).security();
        assertThat(second.getSuccessfulLoginCount()).isEqualTo(2L);
        assertThat(second.getPreviousSuccessfulLoginAt()).isEqualTo(lastLogin);
        assertThat(second.getAccountLocked()).isFalse();
    }

    @Test
    void recordSuccessToleratesNullCounters() {
        UserLoginSecurity s = UserLoginSecurity.builder().userId("u").successfulLoginCount(null).build();
        when(repository.findByUserId("u")).thenReturn(Optional.of(s));
        assertThat(service.recordSuccess(new RecordSuccessInput("u", "ip")).security().getSuccessfulLoginCount()).isEqualTo(1L);
    }

    @Test
    void recordFailureCountsAndOnlyLocksWhenAsked() {
        when(repository.findByUserId("u")).thenReturn(Optional.empty());

        UserLoginSecurity soft = service.recordFailure(new RecordFailureInput("u", "1.1.1.1", false)).security();
        assertThat(soft.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(soft.getFailedLoginCount()).isEqualTo(1L);
        assertThat(soft.getAccountLocked()).isFalse();
        assertThat(soft.getLastFailedLoginIp()).isEqualTo("1.1.1.1");

        when(repository.findByUserId("u")).thenReturn(Optional.of(soft));
        UserLoginSecurity hard = service.recordFailure(new RecordFailureInput("u", "1.1.1.2", true)).security();
        assertThat(hard.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(hard.getAccountLocked()).isTrue();
        assertThat(hard.getLockReason()).isEqualTo("Too many failed login attempts");
        assertThat(hard.getLockedAt()).isNotNull();
    }

    @Test
    void recordFailureToleratesNullCounters() {
        UserLoginSecurity s = UserLoginSecurity.builder().userId("u").failedLoginAttempts(null).failedLoginCount(null).build();
        when(repository.findByUserId("u")).thenReturn(Optional.of(s));
        UserLoginSecurity out = service.recordFailure(new RecordFailureInput("u", "ip", false)).security();
        assertThat(out.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(out.getFailedLoginCount()).isEqualTo(1L);
    }
}
