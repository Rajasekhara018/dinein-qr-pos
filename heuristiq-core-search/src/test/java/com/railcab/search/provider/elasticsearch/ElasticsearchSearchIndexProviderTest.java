package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.SearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchSearchIndexProviderTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;
    @Mock
    private IndexOperations indexOperations;
    @Mock
    private ElasticsearchQueryTranslator queryTranslator;

    private ElasticsearchSearchIndexProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ElasticsearchSearchIndexProvider(elasticsearchOperations, queryTranslator);
        lenient().when(elasticsearchOperations.indexOps(ElasticsearchDocument.class)).thenReturn(indexOperations);
    }

    @Test
    void indexCreatesIndexWhenMissingThenSaves() {
        when(indexOperations.exists()).thenReturn(false);
        SearchDocument document = new SearchDocument();
        document.setId("ride:1");
        document.setText1("COMPLETED");
        when(elasticsearchOperations.save(any(ElasticsearchDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        SearchDocument result = provider.index(document);

        verify(indexOperations).createWithMapping();
        ArgumentCaptor<ElasticsearchDocument> stored = ArgumentCaptor.forClass(ElasticsearchDocument.class);
        verify(elasticsearchOperations).save(stored.capture());
        assertThat(stored.getValue().getId()).isEqualTo("ride:1");
        assertThat(stored.getValue().getText1()).isEqualTo("COMPLETED");
        assertThat(result).usingRecursiveComparison().isEqualTo(document);
    }

    @Test
    void indexSkipsCreateWhenIndexAlreadyExists() {
        when(indexOperations.exists()).thenReturn(true);
        when(elasticsearchOperations.save(any(ElasticsearchDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        provider.index(new SearchDocument());

        verify(indexOperations, never()).createWithMapping();
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingIndexGetsKeywordLengthLimitsWithoutTruncatingPermissions() {
        when(indexOperations.exists()).thenReturn(true);
        when(elasticsearchOperations.save(any(ElasticsearchDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        SearchDocument document = new SearchDocument();
        document.setText7("USER_READ,".repeat(4000));

        SearchDocument result = provider.index(document);
        provider.index(document);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.document.Document> mapping =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.document.Document.class);
        verify(indexOperations).putMapping(mapping.capture());
        java.util.Map<String, Object> properties = (java.util.Map<String, Object>) mapping.getValue().get("properties");
        for (int slot = 1; slot <= 15; slot++) {
            java.util.Map<String, Object> field = (java.util.Map<String, Object>) properties.get("text" + slot);
            java.util.Map<String, Object> fields = (java.util.Map<String, Object>) field.get("fields");
            assertThat((java.util.Map<String, Object>) fields.get("keyword")).containsEntry("ignore_above", 8191);
        }
        assertThat(result.getText7()).isEqualTo(document.getText7());
    }

    @Test
    void deleteUsesCompositeId() {
        provider.delete("RIDE", "ride-1");

        verify(elasticsearchOperations).delete("ride:ride-1", ElasticsearchDocument.class);
    }

    @Test
    void findOneUsesCompositeId() {
        when(indexOperations.exists()).thenReturn(true);
        ElasticsearchDocument stored = new ElasticsearchDocument();
        stored.setModelKey("ride-1");
        when(elasticsearchOperations.get("ride:ride-1", ElasticsearchDocument.class)).thenReturn(stored);

        SearchDocument result = provider.findOne("RIDE", "ride-1");

        assertThat(result.getModelKey()).isEqualTo("ride-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchMapsHitsToPage() {
        when(indexOperations.exists()).thenReturn(true);
        SearchQuery query = new SearchQuery(SearchModel.RIDE, null);
        Pageable pageable = PageRequest.of(0, 20);
        CriteriaQuery criteriaQuery = mock(CriteriaQuery.class);
        when(queryTranslator.translate(query)).thenReturn(criteriaQuery);

        ElasticsearchDocument stored = new ElasticsearchDocument();
        stored.setModelKey("k1");
        SearchHit<ElasticsearchDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(stored);
        SearchHits<ElasticsearchDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);
        when(elasticsearchOperations.search(criteriaQuery, ElasticsearchDocument.class)).thenReturn(hits);

        Page<SearchDocument> page = provider.search(query, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(SearchDocument::getModelKey).containsExactly("k1");
        verify(criteriaQuery).setPageable(pageable);
    }

    @org.junit.jupiter.api.Test
    void textSlotsAreSortedOnTheirKeywordSubFieldOnlyInsideTheProvider() {
        org.springframework.data.domain.Pageable sorted = ElasticsearchSearchIndexProvider.sortableOnKeywords(
                org.springframework.data.domain.PageRequest.of(1, 10, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("text3"), org.springframework.data.domain.Sort.Order.asc("date1"))));

        org.assertj.core.api.Assertions.assertThat(sorted.getSort().getOrderFor("text3.keyword").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        org.assertj.core.api.Assertions.assertThat(sorted.getSort().getOrderFor("date1")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(sorted.getPageNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(ElasticsearchSearchIndexProvider.sortableOnKeywords(
                org.springframework.data.domain.PageRequest.of(0, 5)).getSort().isUnsorted()).isTrue();
    }
}
