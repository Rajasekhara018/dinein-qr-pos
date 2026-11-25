package com.heuristiq.identity.biometric.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.heuristiq.identity.biometric.domain.BiometricChallenge;
import com.heuristiq.identity.biometric.domain.BiometricCredential;
import com.heuristiq.identity.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.identity.biometric.domain.BiometricException;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/** The MongoDB stores through their template: indexes, document shape, and the conditions every update carries. */
class MongoBiometricStoresTest {

    private static final Instant AT = Instant.parse("2026-11-22T10:00:00Z");

    private static MongoTemplate mongo(IndexOperations indexes) {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(anyString())).thenReturn(indexes);
        return mongo;
    }

    @Test
    void credentialStoreCreatesItsIndexesIncludingThePartialUniqueOne() {
        IndexOperations indexes = mock(IndexOperations.class);
        new MongoBiometricCredentialStore(mongo(indexes));

        ArgumentCaptor<IndexDefinition> created = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexes, times(4)).ensureIndex(created.capture());
        IndexDefinition unique = created.getAllValues().get(3);
        assertThat(unique.getIndexOptions()).containsEntry("unique", true).containsKey("partialFilterExpression");
        assertThat(unique.getIndexKeys()).containsKeys("applicationCode", "installationId");
    }

    @Test
    void credentialsRoundTripThroughTheirDocumentShape() {
        MongoTemplate mongo = mongo(mock(IndexOperations.class));
        MongoBiometricCredentialStore store = new MongoBiometricCredentialStore(mongo);
        BiometricCredential credential = JdbcBiometricStoresTest.credential("c1", "u1", "inst-0001", AT);

        store.insert(credential);
        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(inserted.capture(), eq("biometric_credentials"));
        assertThat(inserted.getValue()).containsEntry("_id", "c1").containsEntry("status", "ACTIVE")
                .containsEntry("installationId", "inst-0001").containsEntry("createdAt", Date.from(AT));

        when(mongo.find(any(Query.class), eq(Document.class), eq("biometric_credentials"))).thenReturn(List.of(inserted.getValue()));
        assertThat(store.findActiveByUser("u1")).containsExactly(credential);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Document.class), eq("biometric_credentials"));
        assertThat(query.getValue().getQueryObject()).containsEntry("userId", "u1").containsEntry("status", "ACTIVE");
    }

    @Test
    void aDuplicateActiveInstallationIsAConflict() {
        MongoTemplate mongo = mongo(mock(IndexOperations.class));
        when(mongo.insert(any(Document.class), eq("biometric_credentials"))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> new MongoBiometricCredentialStore(mongo).insert(JdbcBiometricStoresTest.credential("c1", "u1", "i-000001", AT)))
                .isInstanceOf(BiometricException.class)
                .extracting(e -> ((BiometricException) e).kind()).isEqualTo(BiometricException.Kind.CONFLICT);
    }

    @Test
    void revokeIsConditionalOnTheCredentialBeingActive() {
        MongoTemplate mongo = mongo(mock(IndexOperations.class));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("biometric_credentials")))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(new MongoBiometricCredentialStore(mongo).revokeIfActive("c1", BiometricRevocationReason.LOGOUT_ALL, AT)).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq("biometric_credentials"));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", "c1").containsEntry("status", "ACTIVE");
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("status", BiometricCredentialStatus.REVOKED.name()).containsEntry("revokedReason", "LOGOUT_ALL");
    }

    @Test
    void aChallengeIsConsumedByOneConditionalFindAndModify() {
        IndexOperations indexes = mock(IndexOperations.class);
        MongoTemplate mongo = mongo(indexes);
        MongoBiometricChallengeStore store = new MongoBiometricChallengeStore(mongo);
        verify(indexes).ensureIndex(any());
        Document used = new Document("_id", "ch1").append("installationId", "inst-0001").append("challenge", "abc")
                .append("createdAt", Date.from(AT)).append("expiresAt", Date.from(AT.plusSeconds(60)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Document.class),
                eq("biometric_challenges"))).thenReturn(used, (Document) null);

        assertThat(store.consume("ch1", AT.plusSeconds(5))).contains(new BiometricChallenge("ch1", "inst-0001", "abc", AT, AT.plusSeconds(60)));
        assertThat(store.consume("ch1", AT.plusSeconds(6))).isEmpty();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(Document.class),
                eq("biometric_challenges"));
        Document criteria = query.getAllValues().get(0).getQueryObject();
        assertThat(criteria).containsEntry("_id", "ch1").containsKey("usedAt").containsKey("expiresAt");
    }

    @Test
    void failuresIncrementInTheOpenWindowOrOpenANewOne() {
        MongoTemplate mongo = mongo(mock(IndexOperations.class));
        MongoBiometricAttemptStore store = new MongoBiometricAttemptStore(mongo);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Document.class),
                eq("biometric_signin_attempts"))).thenReturn(null, new Document("failures", 2));

        assertThat(store.recordFailure("inst-0001", AT, Duration.ofMinutes(15))).isEqualTo(1);
        ArgumentCaptor<Document> opened = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(opened.capture(), eq("biometric_signin_attempts"));
        assertThat(opened.getValue()).containsEntry("_id", "inst-0001").containsEntry("failures", 1)
                .containsEntry("expiresAt", Date.from(AT.plus(Duration.ofMinutes(15))));

        assertThat(store.recordFailure("inst-0001", AT.plusSeconds(5), Duration.ofMinutes(15))).isEqualTo(2);
    }

    @Test
    void aLostRaceToOpenTheWindowCountsOnTheWinnersWindow() {
        MongoTemplate mongo = mongo(mock(IndexOperations.class));
        MongoBiometricAttemptStore store = new MongoBiometricAttemptStore(mongo);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Document.class),
                eq("biometric_signin_attempts"))).thenReturn(null, new Document("failures", 2));
        when(mongo.insert(any(Document.class), eq("biometric_signin_attempts"))).thenThrow(new DuplicateKeyException("race"));

        assertThat(store.recordFailure("inst-0001", AT, Duration.ofMinutes(15))).isEqualTo(2);
    }

    @Test
    void storesSurviveAnIndexProblemAtStartup() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(anyString())).thenThrow(new IllegalStateException("no permission"));

        new MongoBiometricCredentialStore(mongo);
        new MongoBiometricChallengeStore(mongo);
        assertThat(new MongoBiometricAttemptStore(mongo).failures("inst-0001", AT)).isZero();
    }
}
