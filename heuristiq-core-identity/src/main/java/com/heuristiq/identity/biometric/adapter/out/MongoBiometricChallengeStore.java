package com.heuristiq.identity.biometric.adapter.out;

import static com.heuristiq.identity.biometric.adapter.out.BiometricAdapterConstants.*;

import com.heuristiq.identity.biometric.application.port.BiometricChallengeStore;
import com.heuristiq.identity.biometric.domain.BiometricChallenge;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * MongoDB store of sign-in challenges (collection {@code biometric_challenges}). Consuming is one {@code findAndModify}
 * conditional on "unused and unexpired", atomic on one document, so exactly one concurrent verification gets it. A TTL
 * index removes a challenge once it has expired.
 */
public class MongoBiometricChallengeStore implements BiometricChallengeStore {

    private static final Logger log = LoggerFactory.getLogger(MongoBiometricChallengeStore.class);

    private final MongoTemplate mongo;

    public MongoBiometricChallengeStore(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            mongo.indexOps(CHALLENGES_COLLECTION).ensureIndex(new Index().on(FIELD_EXPIRES_AT, Sort.Direction.ASC)
                    .expire(TTL_AT_EXPIRY_SECONDS).named(INDEX_CHALLENGES_TTL));
        } catch (RuntimeException e) {
            log.warn("biometric.challenges.index-failed reason={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void save(BiometricChallenge challenge) {
        mongo.insert(new Document()
                .append(FIELD_ID, challenge.challengeId())
                .append(FIELD_INSTALLATION_ID, challenge.installationId())
                .append(FIELD_CHALLENGE, challenge.challenge())
                .append(FIELD_CREATED_AT, Date.from(challenge.createdAt()))
                .append(FIELD_EXPIRES_AT, Date.from(challenge.expiresAt()))
                .append(FIELD_USED_AT, null), CHALLENGES_COLLECTION);
    }

    @Override
    public Optional<BiometricChallenge> consume(String challengeId, Instant now) {
        Query query = Query.query(Criteria.where(FIELD_ID).is(challengeId).and(FIELD_USED_AT).is(null)
                .and(FIELD_EXPIRES_AT).gt(Date.from(now)));
        Document used = mongo.findAndModify(query, new Update().set(FIELD_USED_AT, Date.from(now)),
                FindAndModifyOptions.options().returnNew(true), Document.class, CHALLENGES_COLLECTION);
        return Optional.ofNullable(used).map(d -> new BiometricChallenge(d.getString(FIELD_ID), d.getString(FIELD_INSTALLATION_ID),
                d.getString(FIELD_CHALLENGE), d.getDate(FIELD_CREATED_AT).toInstant(), d.getDate(FIELD_EXPIRES_AT).toInstant()));
    }
}
