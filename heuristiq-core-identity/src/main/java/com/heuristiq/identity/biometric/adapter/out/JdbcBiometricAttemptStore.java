package com.heuristiq.identity.biometric.adapter.out;

import static com.heuristiq.identity.biometric.adapter.out.BiometricAdapterConstants.*;

import com.heuristiq.identity.biometric.application.port.BiometricAttemptStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL counter of failed biometric sign-ins ({@code biometric_signin_attempts}): one row per installation, counted by
 * an atomic upsert so concurrent failures on several UAM instances all count.
 */
public class JdbcBiometricAttemptStore implements BiometricAttemptStore {

    private final JdbcTemplate jdbc;

    public JdbcBiometricAttemptStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int failures(String installationId, Instant now) {
        List<Integer> found = jdbc.queryForList(SELECT_FAILURES_SQL, Integer.class, installationId, Timestamp.from(now));
        return found.isEmpty() || found.get(0) == null ? 0 : found.get(0);
    }

    @Override
    public int recordFailure(String installationId, Instant now, Duration window) {
        jdbc.update(DELETE_EXPIRED_ATTEMPTS_SQL, Timestamp.from(now));
        Integer failures = jdbc.queryForObject(RECORD_FAILURE_SQL, Integer.class, installationId, Timestamp.from(now),
                Timestamp.from(now.plus(window)));
        return failures == null ? 0 : failures;
    }

    @Override
    public void clear(String installationId) {
        jdbc.update(DELETE_ATTEMPTS_SQL, installationId);
    }
}
