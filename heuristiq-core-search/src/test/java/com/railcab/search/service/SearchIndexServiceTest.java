package com.heuristiq.search.service;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.provider.SearchIndexPort;
import com.heuristiq.search.query.SearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchIndexService is now just a resilience-wrapped delegate to whichever {@link SearchIndexPort}
 * is active -- engine-specific behavior (index management, query translation, hit mapping) is
 * covered by that port implementation's own tests (see ElasticsearchSearchIndexProviderTest).
 */
@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    @Mock
    private SearchIndexPort searchIndexPort;

    private SearchIndexService service;

    @BeforeEach
    void setUp() {
        service = new SearchIndexService(searchIndexPort, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    void indexDelegatesToPort() {
        SearchDocument document = new SearchDocument();
        document.setId("ride:1");
        document.setModel("RIDE");
        when(searchIndexPort.index(document)).thenReturn(document);

        SearchDocument result = service.index(document);

        verify(searchIndexPort).index(document);
        assertThat(result).isSameAs(document);
    }

    @Test
    void deleteDelegatesToPortWithModelAndModelKey() {
        service.delete("RIDE", "ride-1");

        verify(searchIndexPort).delete("RIDE", "ride-1");
    }

    @Test
    void findOneDelegatesToPort() {
        SearchDocument document = new SearchDocument();
        when(searchIndexPort.findOne("RIDE", "ride-1")).thenReturn(document);

        SearchDocument result = service.findOne("RIDE", "ride-1");

        assertThat(result).isSameAs(document);
    }

    @Test
    void searchDelegatesToPort() {
        SearchQuery query = new SearchQuery(SearchModel.RIDE, null);
        Pageable pageable = PageRequest.of(0, 20);
        SearchDocument document = new SearchDocument();
        Page<SearchDocument> page = new PageImpl<>(List.of(document), pageable, 1);
        when(searchIndexPort.search(query, pageable)).thenReturn(page);

        Page<SearchDocument> result = service.search(query, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(document);
    }

    @Test
    void compositeIdLowercasesModelAndJoinsWithModelKey() {
        assertThat(SearchIndexService.compositeId("RIDE", "ride-1")).isEqualTo("ride:ride-1");
    }
}
