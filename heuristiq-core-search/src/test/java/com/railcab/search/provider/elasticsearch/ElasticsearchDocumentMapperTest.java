package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.document.SearchDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchDocumentMapperTest {

    private static Set<String> properties(Class<?> type) {
        return Arrays.stream(BeanUtils.getPropertyDescriptors(type))
                .map(PropertyDescriptor::getName)
                .filter(name -> !name.equals("class"))
                .collect(Collectors.toSet());
    }

    @Test
    void theStorageClassCarriesExactlyTheSlotsOfTheNeutralDocument() {
        // A slot added to one class but not the other would be silently dropped when indexing or reading.
        assertThat(properties(ElasticsearchDocument.class)).isEqualTo(properties(SearchDocument.class));
    }

    @Test
    void everyKindOfSlotSurvivesAFullRoundTrip() {
        SearchDocument document = SearchDocument.builder().id("ride:1").model("RIDE").modelKey("1").ownerKey("owner")
                .text1("a").text15("z").amount1(12.5).amount5(1.0).number1(7L).number5(9L)
                .date1(Instant.parse("2026-01-01T00:00:00Z")).datetime5(Instant.parse("2026-02-02T10:00:00Z"))
                .createdAt(Instant.parse("2026-03-03T00:00:00Z")).updatedAt(Instant.parse("2026-04-04T00:00:00Z")).build();

        SearchDocument back = ElasticsearchDocumentMapper.toDocument(ElasticsearchDocumentMapper.toStored(document));

        assertThat(back).usingRecursiveComparison().isEqualTo(document);
    }

    @Test
    void aMissingStoredDocumentIsNull() {
        assertThat(ElasticsearchDocumentMapper.toDocument(null)).isNull();
    }
}
