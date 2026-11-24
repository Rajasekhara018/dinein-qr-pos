package com.heuristiq.gateway.identity.repository.mongo;

import com.heuristiq.gateway.identity.persistence.mongo.GatewayUamUserDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UamUserStatusMongoRepositoryAdapterTest {

    private final MongoTemplate template = mock(MongoTemplate.class);
    private final UamUserStatusMongoRepositoryAdapter adapter = new UamUserStatusMongoRepositoryAdapter(template);

    @Test
    void activeUserIsQueriedByIdAndActiveStatus() {
        when(template.exists(any(Query.class), eq(GatewayUamUserDocument.class))).thenReturn(true);

        assertThat(adapter.isActive("u1")).isTrue();

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(template).exists(q.capture(), eq(GatewayUamUserDocument.class));
        assertThat(q.getValue().getQueryObject()).containsEntry("_id", "u1").containsEntry("status", "ACTIVE");
    }

    @Test
    void missingOrInactiveUserIsNotActive() {
        when(template.exists(any(Query.class), eq(GatewayUamUserDocument.class))).thenReturn(false);

        assertThat(adapter.isActive("u2")).isFalse();
    }
}
