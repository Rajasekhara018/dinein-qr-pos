package com.heuristiq.identity.biometric.adapter.out;

import static com.heuristiq.identity.biometric.adapter.out.BiometricAdapterConstants.*;

import com.heuristiq.identity.biometric.application.port.BiometricCredentialStore;
import com.heuristiq.identity.biometric.domain.BiometricCredential;
import com.heuristiq.identity.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.identity.biometric.domain.BiometricException;
import com.heuristiq.identity.biometric.domain.BiometricKeyAlgorithm;
import com.heuristiq.identity.biometric.domain.BiometricPlatform;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL store of device credentials ({@code biometric_credentials}, migration V43). A partial unique index keeps at
 * most one ACTIVE row per application and installation; every status change is conditional on {@code status = 'ACTIVE'}.
 */
public class JdbcBiometricCredentialStore implements BiometricCredentialStore {

    private final JdbcTemplate jdbc;

    public JdbcBiometricCredentialStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(BiometricCredential c) {
        try {
            jdbc.update(INSERT_CREDENTIAL_SQL, c.credentialId(), c.userId(), c.tenantId(), c.applicationCode(), c.installationId(),
                    c.publicKey(), name(c.keyAlgorithm()), name(c.platform()), c.deviceName(), name(c.status()), name(c.revokedReason()),
                    ts(c.createdAt()), ts(c.lastUsedAt()), ts(c.expiresAt()), ts(c.revokedAt()), ts(c.updatedAt()));
        } catch (DuplicateKeyException e) {
            throw BiometricException.conflict();
        }
    }

    @Override
    public Optional<BiometricCredential> findById(String credentialId) {
        return jdbc.query(SELECT_CREDENTIAL_BY_ID_SQL, JdbcBiometricCredentialStore::map, credentialId).stream().findFirst();
    }

    @Override
    public List<BiometricCredential> findActiveByInstallation(String applicationCode, String installationId) {
        return jdbc.query(SELECT_ACTIVE_BY_INSTALLATION_SQL, JdbcBiometricCredentialStore::map, applicationCode, installationId);
    }

    @Override
    public Optional<BiometricCredential> findLatestByInstallation(String applicationCode, String installationId) {
        return jdbc.query(SELECT_LATEST_BY_INSTALLATION_SQL, JdbcBiometricCredentialStore::map, applicationCode, installationId)
                .stream().findFirst();
    }

    @Override
    public List<BiometricCredential> findActiveByUser(String userId) {
        return jdbc.query(SELECT_ACTIVE_BY_USER_SQL, JdbcBiometricCredentialStore::map, userId);
    }

    @Override
    public boolean revokeIfActive(String credentialId, BiometricRevocationReason reason, Instant at) {
        return jdbc.update(END_IF_ACTIVE_SQL, BiometricCredentialStatus.REVOKED.name(), name(reason), ts(at), ts(at), credentialId) == 1;
    }

    @Override
    public boolean expireIfActive(String credentialId, Instant at) {
        return jdbc.update(END_IF_ACTIVE_SQL, BiometricCredentialStatus.EXPIRED.name(), BiometricRevocationReason.INACTIVITY.name(),
                ts(at), ts(at), credentialId) == 1;
    }

    @Override
    public boolean touch(String credentialId, Instant lastUsedAt, Instant expiresAt) {
        return jdbc.update(TOUCH_SQL, ts(lastUsedAt), ts(expiresAt), ts(lastUsedAt), credentialId) == 1;
    }

    private static BiometricCredential map(ResultSet rs, int row) throws SQLException {
        String reason = rs.getString("revoked_reason");
        return new BiometricCredential(
                rs.getString("credential_id"),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getString("application_code"),
                rs.getString("installation_id"),
                rs.getString("public_key"),
                BiometricKeyAlgorithm.valueOf(rs.getString("key_algorithm")),
                BiometricPlatform.valueOf(rs.getString("platform")),
                rs.getString("device_name"),
                BiometricCredentialStatus.valueOf(rs.getString("status")),
                reason == null ? null : BiometricRevocationReason.valueOf(reason),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("last_used_at")),
                instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("revoked_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
