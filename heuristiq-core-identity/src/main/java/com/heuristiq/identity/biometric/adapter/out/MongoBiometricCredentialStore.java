package com.heuristiq.identity.biometric.adapter.out;

import static com.heuristiq.identity.biometric.adapter.out.BiometricAdapterConstants.*;

import com.heuristiq.identity.biometric.application.port.BiometricCredentialStore;
import com.heuristiq.identity.biometric.domain.BiometricCredential;
import com.heuristiq.identity.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.identity.biometric.domain.BiometricException;
import com.heuristiq.identity.biometric.domain.BiometricKeyAlgorithm;
import com.heuristiq.identity.biometric.domain.BiometricPlatform;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * MongoDB store of device credentials (collection {@code biometric_credentials}, same shape as the PostgreSQL table). A
 * partial unique index keeps at most one ACTIVE document per application and installation; every status change is a
 * single-document conditional update on {@code status: ACTIVE}, which MongoDB applies atomically.
 */
public class MongoBiometricCredentialStore implements BiometricCredentialStore {

    private static final Logger log = LoggerFactory.getLogger(MongoBiometricCredentialStore.class);

    private final MongoTemplate mongo;

    public MongoBiometricCredentialStore(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            var indexes = mongo.indexOps(CREDENTIALS_COLLECTION);
            indexes.ensureIndex(new Index().on(FIELD_USER_ID, Sort.Direction.ASC).on(FIELD_APPLICATION_CODE, Sort.Direction.ASC)
                    .on(FIELD_INSTALLATION_ID, Sort.Direction.ASC).named(INDEX_USER_APP_INSTALLATION));
            indexes.ensureIndex(new Index().on(FIELD_INSTALLATION_ID, Sort.Direction.ASC).named(INDEX_INSTALLATION));
            indexes.ensureIndex(new Index().on(FIELD_STATUS, Sort.Direction.ASC).named(INDEX_STATUS));
            indexes.ensureIndex(new Index().on(FIELD_APPLICATION_CODE, Sort.Direction.ASC).on(FIELD_INSTALLATION_ID, Sort.Direction.ASC)
                    .unique().partial(PartialIndexFilter.of(Criteria.where(FIELD_STATUS).is(STATUS_ACTIVE)))
                    .named(INDEX_ACTIVE_INSTALLATION));
        } catch (RuntimeException e) {
            log.warn("biometric.credentials.index-failed reason={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void insert(BiometricCredential c) {
        try {
            mongo.insert(new Document()
                    .append(FIELD_ID, c.credentialId())
                    .append(FIELD_USER_ID, c.userId())
                    .append(FIELD_TENANT_ID, c.tenantId())
                    .append(FIELD_APPLICATION_CODE, c.applicationCode())
                    .append(FIELD_INSTALLATION_ID, c.installationId())
                    .append(FIELD_PUBLIC_KEY, c.publicKey())
                    .append(FIELD_KEY_ALGORITHM, name(c.keyAlgorithm()))
                    .append(FIELD_PLATFORM, name(c.platform()))
                    .append(FIELD_DEVICE_NAME, c.deviceName())
                    .append(FIELD_STATUS, name(c.status()))
                    .append(FIELD_REVOKED_REASON, name(c.revokedReason()))
                    .append(FIELD_CREATED_AT, date(c.createdAt()))
                    .append(FIELD_LAST_USED_AT, date(c.lastUsedAt()))
                    .append(FIELD_EXPIRES_AT, date(c.expiresAt()))
                    .append(FIELD_REVOKED_AT, date(c.revokedAt()))
                    .append(FIELD_UPDATED_AT, date(c.updatedAt())), CREDENTIALS_COLLECTION);
        } catch (DuplicateKeyException e) {
            throw BiometricException.conflict();
        }
    }

    @Override
    public Optional<BiometricCredential> findById(String credentialId) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where(FIELD_ID).is(credentialId)), Document.class,
                CREDENTIALS_COLLECTION)).map(MongoBiometricCredentialStore::map);
    }

    @Override
    public List<BiometricCredential> findActiveByInstallation(String applicationCode, String installationId) {
        return find(Query.query(Criteria.where(FIELD_APPLICATION_CODE).is(applicationCode).and(FIELD_INSTALLATION_ID).is(installationId)
                .and(FIELD_STATUS).is(STATUS_ACTIVE)).with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)));
    }

    @Override
    public Optional<BiometricCredential> findLatestByInstallation(String applicationCode, String installationId) {
        return find(Query.query(Criteria.where(FIELD_APPLICATION_CODE).is(applicationCode).and(FIELD_INSTALLATION_ID).is(installationId))
                .with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)).limit(1)).stream().findFirst();
    }

    @Override
    public List<BiometricCredential> findActiveByUser(String userId) {
        return find(Query.query(Criteria.where(FIELD_USER_ID).is(userId).and(FIELD_STATUS).is(STATUS_ACTIVE))
                .with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)));
    }

    @Override
    public boolean revokeIfActive(String credentialId, BiometricRevocationReason reason, Instant at) {
        return end(credentialId, BiometricCredentialStatus.REVOKED, reason, at);
    }

    @Override
    public boolean expireIfActive(String credentialId, Instant at) {
        return end(credentialId, BiometricCredentialStatus.EXPIRED, BiometricRevocationReason.INACTIVITY, at);
    }

    @Override
    public boolean touch(String credentialId, Instant lastUsedAt, Instant expiresAt) {
        Update update = new Update().set(FIELD_LAST_USED_AT, date(lastUsedAt)).set(FIELD_EXPIRES_AT, date(expiresAt))
                .set(FIELD_UPDATED_AT, date(lastUsedAt));
        return mongo.updateFirst(activeById(credentialId), update, CREDENTIALS_COLLECTION).getModifiedCount() == 1;
    }

    private boolean end(String credentialId, BiometricCredentialStatus status, BiometricRevocationReason reason, Instant at) {
        Update update = new Update().set(FIELD_STATUS, status.name()).set(FIELD_REVOKED_REASON, reason.name())
                .set(FIELD_REVOKED_AT, date(at)).set(FIELD_UPDATED_AT, date(at));
        return mongo.updateFirst(activeById(credentialId), update, CREDENTIALS_COLLECTION).getModifiedCount() == 1;
    }

    private static Query activeById(String credentialId) {
        return Query.query(Criteria.where(FIELD_ID).is(credentialId).and(FIELD_STATUS).is(STATUS_ACTIVE));
    }

    private List<BiometricCredential> find(Query query) {
        List<BiometricCredential> found = new ArrayList<>();
        for (Document document : mongo.find(query, Document.class, CREDENTIALS_COLLECTION)) {
            found.add(map(document));
        }
        return found;
    }

    private static BiometricCredential map(Document d) {
        String reason = d.getString(FIELD_REVOKED_REASON);
        return new BiometricCredential(
                d.getString(FIELD_ID),
                d.getString(FIELD_USER_ID),
                d.getString(FIELD_TENANT_ID),
                d.getString(FIELD_APPLICATION_CODE),
                d.getString(FIELD_INSTALLATION_ID),
                d.getString(FIELD_PUBLIC_KEY),
                BiometricKeyAlgorithm.valueOf(d.getString(FIELD_KEY_ALGORITHM)),
                BiometricPlatform.valueOf(d.getString(FIELD_PLATFORM)),
                d.getString(FIELD_DEVICE_NAME),
                BiometricCredentialStatus.valueOf(d.getString(FIELD_STATUS)),
                reason == null ? null : BiometricRevocationReason.valueOf(reason),
                instant(d.getDate(FIELD_CREATED_AT)),
                instant(d.getDate(FIELD_LAST_USED_AT)),
                instant(d.getDate(FIELD_EXPIRES_AT)),
                instant(d.getDate(FIELD_REVOKED_AT)),
                instant(d.getDate(FIELD_UPDATED_AT)));
    }

    private static Date date(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
