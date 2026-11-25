package com.heuristiq.search.provider.solr;

import com.heuristiq.search.document.SearchDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SolrDocumentMapperTest {

    @Test
    void everyKindOfSlotSurvivesAFullRoundTripThroughSolrFields() {
        SearchDocument document = SearchDocument.builder().id("ride:1").model("RIDE").modelKey("1").ownerKey("owner")
                .text1("a").text15("z").amount1(12.5).amount5(1.0).number1(7L).number5(9L)
                .date1(Instant.parse("2026-01-01T00:00:00Z")).datetime5(Instant.parse("2026-02-02T10:00:00Z"))
                .createdAt(Instant.parse("2026-03-03T00:00:00Z")).updatedAt(Instant.parse("2026-04-04T00:00:00Z")).build();

        Map<String, Object> stored = SolrDocumentMapper.toSolr(document);

        assertThat(stored).containsKeys("id", "model_s", "modelKey_s", "ownerKey_s", "text1_s", "text1_t", "text15_s", "amount1_d",
                "number5_l", "date1_dt", "datetime5_dt", "createdAt_dt", "updatedAt_dt");
        assertThat(stored).doesNotContainKeys("text2_s", "amount2_d");
        assertThat(SolrDocumentMapper.fromSolr(stored)).usingRecursiveComparison().isEqualTo(document);
    }

    @Test
    void multiValuedAnswersAreReadAsTheirFirstValue() {
        SearchDocument document = SolrDocumentMapper.fromSolr(Map.of("id", "x", "text3_s", java.util.List.of("one", "two"), "_version_", 1L));

        assertThat(document.getText3()).isEqualTo("one");
    }
}
