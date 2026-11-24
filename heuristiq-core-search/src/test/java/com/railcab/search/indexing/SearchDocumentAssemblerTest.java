package com.heuristiq.search.indexing;

import com.heuristiq.search.document.SearchDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchDocumentAssemblerTest {

    private final SearchDocumentAssembler assembler = new SearchDocumentAssembler();

    @Test
    void assemblesCompositeIdAndPresentFields() {
        Map<String, Object> payload = Map.of(
                "ownerKey", "vendor-1",
                "text1", "MG Road",
                "amount1", 245.5,
                "date1", "2026-07-19T10:00:00Z");

        SearchDocument doc = assembler.assemble("RIDE", "ride-123", payload);

        assertThat(doc.getId()).isEqualTo("ride:ride-123");
        assertThat(doc.getModel()).isEqualTo("RIDE");
        assertThat(doc.getModelKey()).isEqualTo("ride-123");
        assertThat(doc.getOwnerKey()).isEqualTo("vendor-1");
        assertThat(doc.getText1()).isEqualTo("MG Road");
        assertThat(doc.getAmount1()).isEqualTo(245.5);
        assertThat(doc.getDate1()).isEqualTo(Instant.parse("2026-07-19T10:00:00Z"));
        assertThat(doc.getUpdatedAt()).isNotNull();
        assertThat(doc.getCreatedAt()).isNotNull();
    }

    @Test
    void leavesAbsentSlotsNull() {
        SearchDocument doc = assembler.assemble("DRIVER", "driver-7", Map.of("text1", "Jane Doe"));

        assertThat(doc.getText1()).isEqualTo("Jane Doe");
        assertThat(doc.getText2()).isNull();
        assertThat(doc.getAmount1()).isNull();
    }

    @Test
    void acceptsNumericDateAsEpochMillis() {
        SearchDocument doc = assembler.assemble("VEHICLE", "veh-1", Map.of("date1", 1_800_000_000_000L));

        assertThat(doc.getDate1()).isEqualTo(Instant.ofEpochMilli(1_800_000_000_000L));
    }

    @Test
    void preservesExplicitCreatedAt() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        SearchDocument doc = assembler.assemble("USER", "user-1", Map.of("createdAt", createdAt.toString()));

        assertThat(doc.getCreatedAt()).isEqualTo(createdAt);
    }
}
