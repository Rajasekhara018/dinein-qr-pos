package com.railcab.nms.adapters.out.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.railcab.nms.domain.model.AuditOutboxEntry;
import com.railcab.nms.domain.model.AuditOutboxStatus;
import com.railcab.nms.domain.model.DeliveryStatus;
import com.railcab.nms.domain.model.NotificationAttempt;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.NotificationLog;
import com.railcab.nms.domain.model.NotificationMessage;

/**
 * Mongo-mode adapters without a database (no Docker needed): field mapping in both directions and the
 * duplicate / lease semantics the services rely on. The real-database counterpart is
 * {@link MongoDeliveryAdaptersContainerTest}.
 */
class MongoDeliveryAdaptersTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 10, 0);

    @Test
    void anAttemptMapsEveryFieldToTheDocumentAndBack() {
        NotificationAttemptMongoSpringRepository spring = mock(NotificationAttemptMongoSpringRepository.class);
        when(spring.save(any())).thenAnswer(i -> i.getArgument(0));
        NotificationAttemptMongoRepositoryAdapter adapter = new NotificationAttemptMongoRepositoryAdapter(spring);
        NotificationAttempt attempt = NotificationAttempt.builder().id("a1").logId("l1").messageId("m1").correlationId("c1")
                .event("E").sourceService("rider-uam").eventCode("E2").triggeringUserId("u1").entityType("USER").entityId("u1")
                .channel(NotificationChannel.WHATSAPP).providerName("meta").providerMessageId("wamid").attemptNumber(2)
                .status(DeliveryStatus.READ).providerStatus("read").errorCode("c").errorMessage("m").recipient("******3210")
                .latencyMs(7L).startedAt(T0).completedAt(T0.plusSeconds(1)).statusUpdatedAt(T0.plusSeconds(2)).version(3L).build();

        NotificationAttempt back = adapter.save(attempt);

        ArgumentCaptor<NotificationAttemptDocument> doc = ArgumentCaptor.forClass(NotificationAttemptDocument.class);
        verify(spring).save(doc.capture());
        assertThat(doc.getValue().getSourceService()).isEqualTo("rider-uam");
        assertThat(doc.getValue().getVersion()).isEqualTo(3L);
        assertThat(doc.getValue().getStatus()).isEqualTo(DeliveryStatus.READ);
        assertThat(back).usingRecursiveComparison().isEqualTo(attempt);
    }

    @Test
    void theReceiptLookupAsksForTheNewestAttempt() {
        NotificationAttemptMongoSpringRepository spring = mock(NotificationAttemptMongoSpringRepository.class);
        NotificationAttemptDocument doc = NotificationAttemptDocument.builder().id("a1").providerMessageId("pm").build();
        when(spring.findFirstByProviderMessageIdOrderByStartedAtDesc("pm")).thenReturn(Optional.of(doc));

        Optional<NotificationAttempt> found = new NotificationAttemptMongoRepositoryAdapter(spring).findLatestByProviderMessageId("pm");

        assertThat(found).get().extracting(NotificationAttempt::getId).isEqualTo("a1");
    }

    @Test
    void theLogAndMessageDocumentsKeepTheOrigin() {
        NotificationLogMongoSpringRepository logSpring = mock(NotificationLogMongoSpringRepository.class);
        when(logSpring.save(any())).thenAnswer(i -> i.getArgument(0));
        when(logSpring.findById("l1")).thenAnswer(i -> Optional.of(NotificationLogDocument.builder().id("l1")
                .sourceService("rider-uam").eventCode("E").triggeringUserId("u1").entityType("USER").entityId("u1")
                .status(DeliveryStatus.DELIVERED).build()));
        NotificationLogMongoRepositoryAdapter logs = new NotificationLogMongoRepositoryAdapter(logSpring);

        NotificationLog saved = logs.save(NotificationLog.builder().sourceService("rider-uam").entityId("u1").build());
        assertThat(saved.getSourceService()).isEqualTo("rider-uam");
        assertThat(saved.getEntityId()).isEqualTo("u1");
        NotificationLog read = logs.findById("l1").orElseThrow();
        assertThat(read.getTriggeringUserId()).isEqualTo("u1");
        assertThat(read.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        NotificationMessageMongoSpringRepository msgSpring = mock(NotificationMessageMongoSpringRepository.class);
        when(msgSpring.insert(any(NotificationMessageDocument.class))).thenAnswer(i -> i.getArgument(0));
        NotificationMessage m = new NotificationMessage();
        m.setMessageId("m1");
        m.setSourceService("rider-uam");
        m.setEntityType("USER");
        NotificationMessage inserted = new NotificationMessageMongoRepositoryAdapter(msgSpring).insert(m);
        assertThat(inserted.getSourceService()).isEqualTo("rider-uam");
        assertThat(inserted.getEntityType()).isEqualTo("USER");
    }

    @Test
    void aDuplicateEventIdIsReportedAsNotStored() {
        MongoTemplate template = mock(MongoTemplate.class);
        AuditOutboxMongoRepositoryAdapter adapter = new AuditOutboxMongoRepositoryAdapter(template);
        AuditOutboxEntry entry = AuditOutboxEntry.builder().eventId("evt-00000001").payload("{}").status(AuditOutboxStatus.PENDING).build();

        assertThat(adapter.insertIfAbsent(entry)).isTrue();
        when(template.insert(any(AuditOutboxDocument.class))).thenThrow(new DuplicateKeyException("dup"));
        assertThat(adapter.insertIfAbsent(entry)).isFalse();
    }

    @Test
    void theLeaseIsWonOnlyWhenTheConditionalUpdateMatchesADocument() {
        MongoTemplate template = mock(MongoTemplate.class);
        AuditOutboxMongoRepositoryAdapter adapter = new AuditOutboxMongoRepositoryAdapter(template);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(AuditOutboxDocument.class))).thenReturn(new AuditOutboxDocument(), (AuditOutboxDocument) null);

        assertThat(adapter.tryLease("id", "me", T0, T0.plusSeconds(60))).isTrue();
        assertThat(adapter.tryLease("id", "other", T0, T0.plusSeconds(60))).isFalse();
    }

    @Test
    void dueEntriesAreQueriedWithTheBatchLimit() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.find(any(Query.class), eq(AuditOutboxDocument.class))).thenReturn(List.of(
                AuditOutboxDocument.builder().id("1").eventId("evt-00000001").status(AuditOutboxStatus.PENDING).build()));

        List<AuditOutboxEntry> due = new AuditOutboxMongoRepositoryAdapter(template).findDue(T0, 25);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(template).find(query.capture(), eq(AuditOutboxDocument.class));
        assertThat(query.getValue().getLimit()).isEqualTo(25);
        assertThat(due).singleElement().extracting(AuditOutboxEntry::getEventId).isEqualTo("evt-00000001");
    }
}
