package com.heuristiq.notification.adapters.out.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mongodb.client.MongoClients;
import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.model.AuditOutboxStatus;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

/**
 * Mongo persistence mode against a real MongoDB (Testcontainers). Skipped, not failed, when Docker is
 * not available, like the other container tests in this module.
 */
@Testcontainers(disabledWithoutDocker = true)
class MongoDeliveryAdaptersContainerTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 10, 0);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static MongoTemplate template;
    private static NotificationAttemptMongoRepositoryAdapter attempts;
    private static AuditOutboxMongoRepositoryAdapter outbox;

    @BeforeAll
    static void connect() {
        template = new MongoTemplate(MongoClients.create(MONGO.getConnectionString()), "nms_delivery_it");
        NmsProperties props = new NmsProperties();
        new NotificationAttemptIndexConfig(template, props).ensureIndexes();
        new AuditOutboxIndexConfig(template).ensureIndexes();
        attempts = new NotificationAttemptMongoRepositoryAdapter(
                new MongoRepositoryFactory(template).getRepository(NotificationAttemptMongoSpringRepository.class));
        outbox = new AuditOutboxMongoRepositoryAdapter(template);
    }

    @AfterEach
    void clean() {
        template.dropCollection(NotificationAttemptDocument.class);
        template.dropCollection(AuditOutboxDocument.class);
        new NotificationAttemptIndexConfig(template, new NmsProperties()).ensureIndexes();
        new AuditOutboxIndexConfig(template).ensureIndexes();
    }

    private NotificationAttempt.NotificationAttemptBuilder attempt(String logId, int number) {
        return NotificationAttempt.builder().logId(logId).messageId("m-1").correlationId("c-1")
                .channel(NotificationChannel.SMS).providerName("digimile").providerMessageId("pm-" + logId + number)
                .attemptNumber(number).status(DeliveryStatus.SENT).startedAt(T0.plusSeconds(number));
    }

    @Test
    void attemptsRoundTripAndComeBackOldestFirst() {
        attempts.save(attempt("l1", 2).build());
        attempts.save(attempt("l1", 1).build());

        assertThat(attempts.findByMessageId("m-1", new PageQuery(0, 10)).content())
                .extracting(NotificationAttempt::getAttemptNumber).containsExactly(1, 2);
        assertThat(attempts.findByCorrelationId("c-1", new PageQuery(0, 10)).totalElements()).isEqualTo(2);
        assertThat(attempts.findLatestByProviderMessageId("pm-l11")).isPresent();
    }

    @Test
    void theSameLogAndAttemptNumberCannotBeStoredTwice() {
        attempts.save(attempt("l1", 1).build());

        assertThatThrownBy(() -> attempts.save(attempt("l1", 1).providerMessageId("other").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aStaleUpdateIsRejectedByTheVersionCheck() {
        NotificationAttempt saved = attempts.save(attempt("l1", 1).build());
        NotificationAttempt a = attempts.findById(saved.getId()).orElseThrow();
        NotificationAttempt b = attempts.findById(saved.getId()).orElseThrow();

        attempts.save(a.toBuilder().status(DeliveryStatus.DELIVERED).build());

        assertThatThrownBy(() -> attempts.save(b.toBuilder().status(DeliveryStatus.FAILED).build()))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private AuditOutboxEntry entry(String eventId) {
        return AuditOutboxEntry.builder().eventId(eventId).payload("{}").status(AuditOutboxStatus.PENDING)
                .nextAttemptAt(T0).createdAt(T0).expireAt(T0.plusDays(7)).build();
    }

    @Test
    void theOutboxStoresAnEventOnceAndHandsOutOneLease() {
        assertThat(outbox.insertIfAbsent(entry("evt-00000001"))).isTrue();
        assertThat(outbox.insertIfAbsent(entry("evt-00000001"))).isFalse();
        String id = outbox.findDue(T0, 10).get(0).getId();

        assertThat(outbox.tryLease(id, "a", T0, T0.plusSeconds(60))).isTrue();
        assertThat(outbox.tryLease(id, "b", T0.plusSeconds(1), T0.plusSeconds(61))).isFalse();
        assertThat(outbox.findDue(T0.plusSeconds(1), 10)).isEmpty();
        assertThat(outbox.tryLease(id, "b", T0.plusSeconds(120), T0.plusSeconds(180))).isTrue();
    }

    @Test
    void outboxStatusesAreTracked() {
        outbox.insertIfAbsent(entry("evt-00000001"));
        outbox.insertIfAbsent(entry("evt-00000002"));
        List<AuditOutboxEntry> due = outbox.findDue(T0, 10);

        outbox.markSent(due.get(0).getId(), T0);
        outbox.markFailed(due.get(1).getId(), 20, T0.plusMinutes(5), "audit down", true);

        assertThat(outbox.countByStatus(AuditOutboxStatus.SENT)).isEqualTo(1);
        assertThat(outbox.countByStatus(AuditOutboxStatus.DEAD)).isEqualTo(1);
        assertThat(outbox.findDue(T0.plusHours(1), 10)).isEmpty();
    }
}
