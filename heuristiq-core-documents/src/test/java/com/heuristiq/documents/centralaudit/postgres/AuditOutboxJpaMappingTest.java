package com.heuristiq.documents.centralaudit.postgres;

import com.heuristiq.documents.centralaudit.OutboxEntry;
import com.heuristiq.documents.centralaudit.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditOutboxJpaMappingTest {

    @Test
    void jpaEntityRoundTripsEveryFieldAndTakesTheGivenId() {
        Instant t = Instant.parse("2026-03-01T00:00:00Z");
        OutboxEntry entry = new OutboxEntry(null, "dms-abc12345", "{\"a\":1}", OutboxStatus.SENT, 1, t, null, null, null, t, t,
                t.plusSeconds(5));

        OutboxEntry back = AuditOutboxJpaEntity.fromDomain(entry, "id-9").toDomain();

        assertEquals("id-9", back.id());
        assertEquals(new OutboxEntry("id-9", "dms-abc12345", "{\"a\":1}", OutboxStatus.SENT, 1, t, null, null, null, t, t,
                t.plusSeconds(5)), back);
    }
}
