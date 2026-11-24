package com.heuristiq.uam.users.repository.mongo;

import com.heuristiq.uam.helper.PhoneIdentity;
import com.heuristiq.uam.users.UserPhoneConstants;
import com.heuristiq.uam.users.persistence.mongo.UamUserDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup step for the "one account per real phone number" rule on Mongo: drops the legacy unique index on the national
 * number alone and fills {@code phoneE164} on documents that predate it.
 *
 * <p>Idempotent and safe on several instances: the index drop tolerates a missing index, and each document is updated
 * only while it still has no {@code phoneE164}. A document whose canonical value is already held by another account is
 * left without one and logged (resolve it by hand). Documents are read in batches ordered by id.</p>
 */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UamUserMongoPhoneMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UamUserMongoPhoneMigration.class);

    private final MongoTemplate mongoTemplate;

    UamUserMongoPhoneMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        dropLegacyIndex();
        backfill();
    }

    private void dropLegacyIndex() {
        var indexOps = mongoTemplate.indexOps(UamUserDocument.class);
        boolean present = indexOps.getIndexInfo().stream()
                .anyMatch(index -> UserPhoneConstants.MONGO_LEGACY_UNIQUE_PHONE_NUMBER.equals(index.getName()));
        if (!present) {
            return;
        }
        try {
            indexOps.dropIndex(UserPhoneConstants.MONGO_LEGACY_UNIQUE_PHONE_NUMBER);
            log.info("Dropped legacy unique index {}", UserPhoneConstants.MONGO_LEGACY_UNIQUE_PHONE_NUMBER);
        } catch (DataAccessException e) {
            // Another instance dropped it first.
            log.debug("Legacy phone index already gone: {}", e.getMessage());
        }
    }

    private void backfill() {
        int updated = 0;
        int skipped = 0;
        String lastId = null;
        while (true) {
            Query query = new Query(Criteria.where(UserPhoneConstants.FIELD_PHONE_E164).exists(false)
                    .and("phoneNumber.number").exists(true));
            if (lastId != null) {
                query.addCriteria(Criteria.where("_id").gt(lastId));
            }
            query.with(Sort.by("_id")).limit(UserPhoneConstants.PHONE_MIGRATION_BATCH_SIZE);
            List<UamUserDocument> batch = mongoTemplate.find(query, UamUserDocument.class);
            if (batch.isEmpty()) {
                break;
            }
            for (UamUserDocument doc : batch) {
                lastId = doc.getId();
                String e164 = PhoneIdentity.e164Of(doc.getPhoneNumber());
                if (e164 == null) {
                    skipped++;
                    continue;
                }
                try {
                    var result = mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(doc.getId())
                                    .and(UserPhoneConstants.FIELD_PHONE_E164).exists(false)),
                            new Update().set(UserPhoneConstants.FIELD_PHONE_E164, e164), UamUserDocument.class);
                    updated += (int) result.getModifiedCount();
                } catch (DuplicateKeyException e) {
                    skipped++;
                    log.warn("User {} shares its phone number with another account; left without a canonical value", doc.getId());
                }
            }
        }
        if (updated > 0 || skipped > 0) {
            log.info("Phone canonical backfill: {} updated, {} skipped", updated, skipped);
        }
    }
}
