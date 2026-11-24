package com.heuristiq.uam.refreshToken.repository.mongo;

import com.mongodb.client.result.UpdateResult;
import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.refreshToken.RefreshTokenConstants;
import com.heuristiq.uam.refreshToken.persistance.mongo.RefreshTokenDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Mongo adapter's conditional updates match only an active (rotation) or under-allowance (grace) document. */
class RefreshTokenMongoRepositoryAdapterTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 8, 10, 0);

    private MongoTemplate mongo;
    private RefreshTokenMongoSpringRepository spring;
    private RefreshTokenMongoRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        spring = mock(RefreshTokenMongoSpringRepository.class);
        adapter = new RefreshTokenMongoRepositoryAdapter(spring, mongo);
    }

    @Test
    void markRotatedOnlyMatchesAnActiveTokenAndSetsTheLink() {
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(RefreshTokenDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null), UpdateResult.acknowledged(0, 0L, null));

        assertThat(adapter.markRotated("id-1", "succ", "fam", AT)).isTrue();
        assertThat(adapter.markRotated("id-1", "succ", "fam", AT)).isFalse();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo, org.mockito.Mockito.times(2)).updateFirst(query.capture(), update.capture(), eq(RefreshTokenDocument.class));
        Document criteria = query.getValue().getQueryObject();
        assertThat(criteria.get("id")).isEqualTo("id-1");
        assertThat(criteria.get("revoked", Document.class)).containsEntry("$ne", true);
        Document set = ((Update) update.getValue()).getUpdateObject().get("$set", Document.class);
        assertThat(set).containsEntry("revoked", true)
                .containsEntry("revokedReason", RefreshTokenConstants.REASON_ROTATED)
                .containsEntry("revokedAt", AT)
                .containsEntry("successorTokenId", "succ")
                .containsEntry("familyId", "fam")
                .containsEntry("audit.updatedAt", AT);
    }

    @Test
    void claimGraceOnlyMatchesARotatedTokenUnderTheAllowanceAndIncrements() {
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(RefreshTokenDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(adapter.claimGraceRedemption("id-1", 2)).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(RefreshTokenDocument.class));
        Document criteria = query.getValue().getQueryObject();
        assertThat(criteria).containsEntry("id", "id-1").containsEntry("revoked", true)
                .containsEntry("revokedReason", RefreshTokenConstants.REASON_ROTATED);
        assertThat(criteria.getList("$or", Document.class)).hasSize(2);
        assertThat(criteria.getList("$or", Document.class).get(1).get("graceRedemptions", Document.class)).containsEntry("$lt", 2);
        assertThat(((Update) update.getValue()).getUpdateObject().get("$inc", Document.class)).containsEntry("graceRedemptions", 1);
    }

    @Test
    void familyLookupAndNewFieldsMapBothWays() {
        RefreshTokenDocument doc = RefreshTokenDocument.builder().id("1").tokenId("t").familyId("fam").successorTokenId("s")
                .revokedAt(AT).graceRedemptions(1).revoked(true).build();
        when(spring.findByFamilyIdAndRevokedFalse("fam")).thenReturn(List.of(doc));
        when(spring.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken found = adapter.findByFamilyIdAndRevokedFalse("fam").get(0);
        assertThat(found.getFamilyId()).isEqualTo("fam");
        assertThat(found.getSuccessorTokenId()).isEqualTo("s");
        assertThat(found.getRevokedAt()).isEqualTo(AT);
        assertThat(found.getGraceRedemptions()).isEqualTo(1);

        RefreshToken saved = adapter.save(found);
        assertThat(saved).usingRecursiveComparison().isEqualTo(found);
    }
}
