package com.heuristiq.uam.biometric.adapter.out;

import static com.heuristiq.uam.biometric.adapter.out.BiometricAdapterConstants.*;

import com.heuristiq.uam.biometric.application.port.BiometricChallengeStore;
import com.heuristiq.uam.biometric.domain.BiometricChallenge;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL store of sign-in challenges ({@code biometric_challenges}). Consuming is one conditional
 * {@code UPDATE ... RETURNING}, so exactly one of several concurrent verifications (on any UAM instance) gets a challenge.
 * Each new challenge also removes those that ended over an hour ago, so the table stays small without a scheduler.
 */
public class JdbcBiometricChallengeStore implements BiometricChallengeStore {

    private final JdbcTemplate jdbc;

    public JdbcBiometricChallengeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(BiometricChallenge challenge) {
        jdbc.update(DELETE_EXPIRED_CHALLENGES_SQL, Timestamp.from(challenge.createdAt().minusSeconds(CHALLENGE_RETENTION_SECONDS)));
        jdbc.update(INSERT_CHALLENGE_SQL, challenge.challengeId(), challenge.installationId(), challenge.challenge(),
                Timestamp.from(challenge.createdAt()), Timestamp.from(challenge.expiresAt()));
    }

    @Override
    public Optional<BiometricChallenge> consume(String challengeId, Instant now) {
        Timestamp at = Timestamp.from(now);
        return jdbc.query(CONSUME_CHALLENGE_SQL, (rs, row) -> new BiometricChallenge(rs.getString("challenge_id"),
                rs.getString("installation_id"), rs.getString("challenge"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()), at, challengeId, at).stream().findFirst();
    }
}
