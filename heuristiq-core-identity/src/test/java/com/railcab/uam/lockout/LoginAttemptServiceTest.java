package com.heuristiq.uam.lockout;

import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase.IsLockedInput;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase.RecordFailureInput;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase.RecordSuccessInput;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase.UnlockInput;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {
    private final Map<String, LoginAttemptState> rows = new HashMap<>();
    private SimpleMeterRegistry meters;
    private LoginAttemptService service;

    private final LoginAttemptStore store = new LoginAttemptStore() {
        @Override
        public Optional<LoginAttemptState> find(String username) {
            return Optional.ofNullable(rows.get(username));
        }

        @Override
        public void save(LoginAttemptState state) {
            rows.put(state.username(), state);
        }

        @Override
        public void delete(String username) {
            rows.remove(username);
        }
    };

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        service = new LoginAttemptService(store, meters, 3, 10);
    }

    private boolean locked(String user) {
        return service.isLocked(new IsLockedInput(user)).locked();
    }

    @Test
    void unknownUserIsNotLocked() {
        assertThat(locked("nobody")).isFalse();
    }

    @Test
    void locksAfterMaxFailuresAndCountsMetrics() {
        service.recordFailure(new RecordFailureInput("Alice "));
        service.recordFailure(new RecordFailureInput("alice"));
        assertThat(locked("alice")).isFalse();

        service.recordFailure(new RecordFailureInput("ALICE"));

        assertThat(locked("alice")).isTrue();
        assertThat(rows.get("alice").failures()).isEqualTo(3);
        assertThat(meters.counter("uam.auth.lockouts").count()).isEqualTo(1.0);
        assertThat(meters.counter("uam.auth.login.attempts", "result", "failure").count()).isEqualTo(3.0);
        assertThat(meters.counter("uam.auth.lockout.active").count()).isEqualTo(1.0);
    }

    @Test
    void successClearsCounterAndUnlockClearsLock() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(new RecordFailureInput("bob"));
        }
        service.recordSuccess(new RecordSuccessInput("bob"));
        assertThat(rows).doesNotContainKey("bob");
        assertThat(meters.counter("uam.auth.login.attempts", "result", "success").count()).isEqualTo(1.0);

        for (int i = 0; i < 3; i++) {
            service.recordFailure(new RecordFailureInput("bob"));
        }
        assertThat(locked("bob")).isTrue();
        service.unlock(new UnlockInput("bob"));
        assertThat(locked("bob")).isFalse();
        assertThat(meters.counter("uam.auth.lockout.admin_unlock").count()).isEqualTo(1.0);
    }

    @Test
    void expiredRowIsDeletedOnCheckAndRestartsCountOnFailure() {
        rows.put("carol", new LoginAttemptState("carol", 2, null, Instant.now().minusSeconds(5)));
        assertThat(locked("carol")).isFalse();
        assertThat(rows).doesNotContainKey("carol");

        rows.put("dave", new LoginAttemptState("dave", 2, null, Instant.now().minusSeconds(5)));
        service.recordFailure(new RecordFailureInput("dave"));
        assertThat(rows.get("dave").failures()).isEqualTo(1);
    }

    @Test
    void elapsedLockIsNotLockedAndLiveCounterWithoutLockIsNotLocked() {
        rows.put("erin", new LoginAttemptState("erin", 3, Instant.now().minusSeconds(1), Instant.now().plusSeconds(60)));
        assertThat(locked("erin")).isFalse();
        rows.put("frank", new LoginAttemptState("frank", 1, null, Instant.now().plusSeconds(60)));
        assertThat(locked("frank")).isFalse();
    }

    @Test
    void blankUsernameSharesTheEmptyKeyAndLimitsAreFlooredAtOne() {
        LoginAttemptService strict = new LoginAttemptService(store, meters, 0, 0);
        strict.recordFailure(new RecordFailureInput(null));
        assertThat(rows).containsKey("");
        assertThat(strict.isLocked(new IsLockedInput("  ")).locked()).isTrue();
    }
}
