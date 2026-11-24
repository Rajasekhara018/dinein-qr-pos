package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** Indexes of the audit outbox: unique event id (idempotent enqueue), drain lookup, and TTL on {@code expireAt}. */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class AuditOutboxIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void ensureIndexes() {
        var ops = mongoTemplate.indexOps(AuditOutboxDocument.class);
        ops.createIndex(new Index().on("eventId", Sort.Direction.ASC).unique());
        ops.createIndex(new Index().on("status", Sort.Direction.ASC).on("nextAttemptAt", Sort.Direction.ASC));
        ops.createIndex(new Index().on("expireAt", Sort.Direction.ASC).expire(Duration.ZERO));
    }
}
