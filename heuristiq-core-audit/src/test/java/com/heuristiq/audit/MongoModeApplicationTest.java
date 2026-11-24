package com.heuristiq.audit;

import com.heuristiq.audit.domain.AuditEventRepository;
import com.heuristiq.audit.adapter.out.lock.MongoSchedulerLock;
import com.heuristiq.audit.domain.SchedulerLock;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whole application on MongoDB (embedded server, the default persistence type): everything in
 * {@link AbstractAuditModeTest} plus Mongo specifics (no SQL beans, TTL and unique indexes exist).
 */
@SpringBootTest(classes = HeuristiqCoreAuditServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=dev,test-mongo",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
class MongoModeApplicationTest extends AbstractAuditModeTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private AuditEventRepository bean;

    @Test
    void mongoBeansAreActiveAndSqlBeansAreNot() {
        assertThat(lock).isInstanceOf(MongoSchedulerLock.class);
        assertThat(bean.getClass().getSimpleName()).contains("Mongo");
        assertThat(context.getBeanNamesForType(javax.sql.DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SchedulerLock.class)).hasSize(1);
    }

    @Test
    void ttlAndUniqueIndexesExistSoMongoExpiresPayloadsWithTheirEvents() {
        var events = mongo.getCollection("audit_events").listIndexes().into(new java.util.ArrayList<Document>());
        assertThat(events).anySatisfy(i -> {
            assertThat(i.get("key", Document.class)).containsKey("expireAt");
            assertThat(i.get("expireAfterSeconds")).isNotNull();
        });
        assertThat(events).anySatisfy(i -> {
            assertThat(i.get("key", Document.class)).containsKey("eventId");
            assertThat(i.getBoolean("unique")).isTrue();
        });
        var payloads = mongo.getCollection("audit_event_payloads").listIndexes().into(new java.util.ArrayList<Document>());
        assertThat(payloads).anySatisfy(i -> {
            assertThat(i.get("key", Document.class)).containsKey("expireAt");
            assertThat(i.get("expireAfterSeconds")).isNotNull();
        });
    }

    @Test
    void purgeDoesNothingWhileAnotherInstanceHoldsTheLeaseOnMongo() {
        MongoSchedulerLock foreign = new MongoSchedulerLock(mongo, java.time.Clock.systemUTC());
        assertThat(foreign.tryAcquire("audit-retention", 60_000)).isTrue();
        assertThat(retentionJob.purge()).isZero();
        foreign.release("audit-retention");
    }
}
