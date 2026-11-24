package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import com.railcab.nms.infrastructure.config.NmsProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** TTL for attempt records: same retention as the log rows they belong to ({@code nms.audit.log-retention-days}). */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class NotificationAttemptIndexConfig {

    private final MongoTemplate mongoTemplate;
    private final NmsProperties props;

    @PostConstruct
    void ensureIndexes() {
        var ops = mongoTemplate.indexOps(NotificationAttemptDocument.class);
        ops.createIndex(new Index()
                .on("startedAt", Sort.Direction.ASC)
                .expire(Duration.ofDays(props.getAudit().getLogRetentionDays())));
        // Created explicitly: Spring Data's auto-index-creation is off by default, so the
        // annotation-declared indexes alone would not exist.
        ops.createIndex(new Index().on("logId", Sort.Direction.ASC).on("attemptNumber", Sort.Direction.ASC).unique());
        ops.createIndex(new Index().on("providerMessageId", Sort.Direction.ASC));
        ops.createIndex(new Index().on("messageId", Sort.Direction.ASC));
        ops.createIndex(new Index().on("correlationId", Sort.Direction.ASC));
        ops.createIndex(new Index().on("tenantId", Sort.Direction.ASC));
    }
}
