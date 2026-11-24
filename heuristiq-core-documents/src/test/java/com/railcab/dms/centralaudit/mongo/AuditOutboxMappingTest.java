package com.railcab.dms.centralaudit.mongo;

import com.railcab.dms.centralaudit.OutboxEntry;
import com.railcab.dms.centralaudit.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditOutboxMappingTest {

    @Test
    void mongoDocumentRoundTripsEveryField() {
        Instant t = Instant.parse("2026-03-01T00:00:00Z");
        OutboxEntry entry = new OutboxEntry("id-1", "dms-abc12345", "{\"a\":1}", OutboxStatus.DEAD, 3, t, t.plusSeconds(1), "me",
                "boom", t, t.plusSeconds(2), t.plusSeconds(3));

        assertEquals(entry, AuditOutboxMongoDocument.fromDomain(entry).toDomain());
    }
}
