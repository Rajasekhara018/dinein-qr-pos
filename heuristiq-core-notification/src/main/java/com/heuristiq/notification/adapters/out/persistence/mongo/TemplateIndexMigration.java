package com.heuristiq.notification.adapters.out.persistence.mongo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Drops the template index that allowed one template per event and channel for the whole platform. Organizations can
 * now own their own template for the same event and channel, so the uniqueness moved to
 * (event, channel, organization); a leftover old index would refuse every organization template.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class TemplateIndexMigration {

    private static final Logger log = LoggerFactory.getLogger(TemplateIndexMigration.class);
    static final String OLD_UNIQUE_INDEX = "event_channel_idx";

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void dropOldUniqueIndex() {
        try {
            mongoTemplate.indexOps(NotificationTemplateDocument.class).dropIndex(OLD_UNIQUE_INDEX);
            log.info("Dropped the old template index {}", OLD_UNIQUE_INDEX);
        } catch (RuntimeException e) {
            log.debug("Old template index {} is not present: {}", OLD_UNIQUE_INDEX, e.getMessage());
        }
    }
}
