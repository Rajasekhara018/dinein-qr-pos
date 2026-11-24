package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.adapters.out.persistence.mongo.NotificationLogDocument;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class NotificationLogIndexConfig {

    private final MongoTemplate mongoTemplate;
    private final NmsProperties props;

    @PostConstruct
    void ensureIndexes() {
        var ops = mongoTemplate.indexOps(NotificationLogDocument.class);
        ops
                .createIndex(new Index()
                        .on("sentAt", Sort.Direction.ASC)
                        .expire(Duration.ofDays(props.getAudit().getLogRetentionDays())));
        // Every read is limited to the caller's organization, so look-ups by it need an index.
        ops.createIndex(new Index().on("tenantId", Sort.Direction.ASC));
    }
}
