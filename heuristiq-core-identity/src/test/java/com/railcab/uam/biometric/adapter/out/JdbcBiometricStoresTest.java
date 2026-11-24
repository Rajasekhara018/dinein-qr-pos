package com.heuristiq.uam.biometric.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.uam.biometric.domain.BiometricChallenge;
import com.heuristiq.uam.biometric.domain.BiometricCredential;
import com.heuristiq.uam.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.uam.biometric.domain.BiometricException;
import com.heuristiq.uam.biometric.domain.BiometricKeyAlgorithm;
import com.heuristiq.uam.biometric.domain.BiometricPlatform;
import com.heuristiq.uam.biometric.domain.BiometricRevocationReason;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** The PostgreSQL stores on the real V43 migration: conditional updates, the one-ACTIVE-per-installation index, atomic counters. */
class JdbcBiometricStoresTest {

    private static final Instant AT = Instant.parse("2026-11-22T10:00:00Z");

    private static EmbeddedPostgres pg;
    private static JdbcTemplate jdbc;

    private JdbcBiometricCredentialStore credentials;
    private JdbcBiometricChallengeStore challenges;
    private JdbcBiometricAttemptStore attempts;

    @BeforeAll
    static void startDatabase() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Flyway.configure().configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        pg.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM biometric_credentials");
        jdbc.update("DELETE FROM biometric_challenges");
        jdbc.update("DELETE FROM biometric_signin_attempts");
        credentials = new JdbcBiometricCredentialStore(jdbc);
        challenges = new JdbcBiometricChallengeStore(jdbc);
        attempts = new JdbcBiometricAttemptStore(jdbc);
    }

    static BiometricCredential credential(String id, String userId, String installation, Instant createdAt) {
        return new BiometricCredential(id, userId, "org-a", "APP", installation, "PUBKEY", BiometricKeyAlgorithm.EC_P256_SHA256,
                BiometricPlatform.ANDROID, "Android phone", BiometricCredentialStatus.ACTIVE, null, createdAt, null,
                createdAt.plus(Duration.ofDays(90)), null, createdAt);
    }

    @Test
    void credentialsRoundTripAndAreFoundByInstallationAndUser() {
        credentials.insert(credential("c1", "u1", "inst-0001", AT));
        credentials.insert(credential("c2", "u1", "inst-0002", AT.plusSeconds(5)));
        credentials.insert(credential("c3", "u2", "inst-0003", AT));

        assertThat(credentials.findById("c1")).contains(credential("c1", "u1", "inst-0001", AT));
        assertThat(credentials.findById("missing")).isEmpty();
        assertThat(credentials.findActiveByUser("u1")).extracting(BiometricCredential::credentialId).containsExactly("c2", "c1");
        assertThat(credentials.findActiveByInstallation("APP", "inst-0001")).extracting(BiometricCredential::credentialId).containsExactly("c1");
        assertThat(credentials.findActiveByInstallation("OTHER", "inst-0001")).isEmpty();
    }

    @Test
    void onlyOneActiveCredentialPerApplicationAndInstallation() {
        credentials.insert(credential("c1", "u1", "inst-0001", AT));

        assertThatThrownBy(() -> credentials.insert(credential("c2", "u2", "inst-0001", AT.plusSeconds(1))))
                .isInstanceOf(BiometricException.class)
                .extracting(e -> ((BiometricException) e).kind()).isEqualTo(BiometricException.Kind.CONFLICT);

        assertThat(credentials.revokeIfActive("c1", BiometricRevocationReason.REPLACED, AT.plusSeconds(2))).isTrue();
        credentials.insert(credential("c2", "u2", "inst-0001", AT.plusSeconds(3)));
        assertThat(credentials.findLatestByInstallation("APP", "inst-0001")).get()
                .extracting(BiometricCredential::credentialId).isEqualTo("c2");
    }

    @Test
    void statusChangesApplyOnlyToAnActiveCredential() {
        credentials.insert(credential("c1", "u1", "inst-0001", AT));

        assertThat(credentials.touch("c1", AT.plusSeconds(60), AT.plus(Duration.ofDays(91)))).isTrue();
        assertThat(credentials.findById("c1").orElseThrow().lastUsedAt()).isEqualTo(AT.plusSeconds(60));
        assertThat(credentials.revokeIfActive("c1", BiometricRevocationReason.PASSWORD_CHANGED, AT.plusSeconds(90))).isTrue();
        assertThat(credentials.revokeIfActive("c1", BiometricRevocationReason.LOGOUT_ALL, AT.plusSeconds(91))).isFalse();
        assertThat(credentials.expireIfActive("c1", AT.plusSeconds(92))).isFalse();
        assertThat(credentials.touch("c1", AT.plusSeconds(93), AT.plusSeconds(94))).isFalse();

        BiometricCredential stored = credentials.findById("c1").orElseThrow();
        assertThat(stored.status()).isEqualTo(BiometricCredentialStatus.REVOKED);
        assertThat(stored.revokedReason()).isEqualTo(BiometricRevocationReason.PASSWORD_CHANGED);
        assertThat(stored.revokedAt()).isEqualTo(AT.plusSeconds(90));
        assertThat(credentials.findActiveByUser("u1")).isEmpty();

        credentials.insert(credential("c2", "u1", "inst-0002", AT));
        assertThat(credentials.expireIfActive("c2", AT.plusSeconds(5))).isTrue();
        assertThat(credentials.findById("c2").orElseThrow().status()).isEqualTo(BiometricCredentialStatus.EXPIRED);
        assertThat(credentials.findById("c2").orElseThrow().revokedReason()).isEqualTo(BiometricRevocationReason.INACTIVITY);
    }

    @Test
    void aChallengeIsConsumedOnceAndNeverAfterItExpires() {
        challenges.save(new BiometricChallenge("ch1", "inst-0001", "abc", AT, AT.plusSeconds(60)));
        challenges.save(new BiometricChallenge("ch2", "inst-0001", "def", AT, AT.plusSeconds(60)));

        assertThat(challenges.consume("ch1", AT.plusSeconds(10))).get().satisfies(c -> {
            assertThat(c.installationId()).isEqualTo("inst-0001");
            assertThat(c.challenge()).isEqualTo("abc");
        });
        assertThat(challenges.consume("ch1", AT.plusSeconds(11))).isEmpty();
        assertThat(challenges.consume("ch2", AT.plusSeconds(60))).isEmpty();
        assertThat(challenges.consume("missing", AT)).isEmpty();
    }

    @Test
    void longExpiredChallengesAreRemovedWhenANewOneIsStored() {
        challenges.save(new BiometricChallenge("old", "inst-0001", "abc", AT, AT.plusSeconds(60)));
        Instant later = AT.plus(Duration.ofHours(2));
        challenges.save(new BiometricChallenge("new", "inst-0001", "def", later, later.plusSeconds(60)));

        assertThat(jdbc.queryForList("SELECT challenge_id FROM biometric_challenges", String.class)).containsExactly("new");
    }

    @Test
    void failuresAreCountedPerInstallationInAWindowThatRestartsWhenItEnds() {
        Duration window = Duration.ofMinutes(15);
        assertThat(attempts.failures("inst-0001", AT)).isZero();

        assertThat(attempts.recordFailure("inst-0001", AT, window)).isEqualTo(1);
        assertThat(attempts.recordFailure("inst-0001", AT.plusSeconds(60), window)).isEqualTo(2);
        assertThat(attempts.recordFailure("inst-0002", AT.plusSeconds(60), window)).isEqualTo(1);
        assertThat(attempts.failures("inst-0001", AT.plusSeconds(120))).isEqualTo(2);

        assertThat(attempts.failures("inst-0001", AT.plus(window))).isZero();
        assertThat(attempts.recordFailure("inst-0001", AT.plus(window).plusSeconds(1), window)).isEqualTo(1);

        attempts.clear("inst-0001");
        assertThat(attempts.failures("inst-0001", AT.plus(window).plusSeconds(2))).isZero();
        assertThat(attempts.failures("inst-0002", AT.plusSeconds(120))).isEqualTo(1);
    }

    @Test
    void theSessionAuthMethodColumnExists() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'user_sessions' "
                + "AND column_name = 'auth_method'", Integer.class)).isEqualTo(1);
    }
}
