package com.heuristiq.audit.adapter.out.persistence.mongo;

import static com.heuristiq.audit.adapter.out.persistence.PersistenceConstants.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Hands out the numeric ids the repository ports use ({@code Long}), which MongoDB does not generate on
 * its own. One counter document per sequence name, advanced with an atomic {@code $inc}, so two
 * instances can never receive the same id. Ids are not reused and may have gaps (a failed insert still
 * consumed its number), exactly like database sequences.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.audit.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class MongoSequence {

    private final MongoTemplate mongo;

    public MongoSequence(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * @param name sequence name, normally the collection the id is for (e.g. {@code audit_events})
     * @return the next value, starting at 1
     */
    public long next(String name) {
        Counter counter = mongo.findAndModify(
                new Query(Criteria.where(FIELD_MONGO_ID).is(name)),
                new Update().inc(FIELD_SEQUENCE_VALUE, 1L),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Counter.class, SEQUENCES_COLLECTION);
        if (counter == null) {
            throw new IllegalStateException("Could not advance sequence " + name);
        }
        return counter.value;
    }

    /** Counter document. */
    private static final class Counter {
        @org.springframework.data.annotation.Id
        String id;
        long value;
    }
}
