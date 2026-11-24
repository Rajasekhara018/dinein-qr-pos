package com.heuristiq.search.service;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.provider.SearchIndexPort;
import com.heuristiq.search.query.SearchQuery;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Resilience-wrapped facade over whichever {@link SearchIndexPort} is active (Elasticsearch by
 * default -- see {@code railcab.search.provider}). Circuit-breaker/retry live here because they're
 * engine-agnostic cross-cutting concerns; engine-specific behavior (index management, query
 * translation) lives entirely inside the port implementation, so swapping engines never touches
 * this class, {@code SearchController}, or {@code SearchDocumentAssembler}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexService {

    private final SearchIndexPort searchIndexPort;
    private final MeterRegistry meterRegistry;

    @CircuitBreaker(name = "search-index", fallbackMethod = "indexFallback")
    @Retry(name = "search-index")
    public SearchDocument index(SearchDocument document) {
        SearchDocument indexed = searchIndexPort.index(document);
        meterRegistry.counter("railcab_search_index_operations_total",
                "model", document.getModel(), "outcome", "success").increment();
        return indexed;
    }

    @SuppressWarnings("unused")
    private SearchDocument indexFallback(SearchDocument document, Exception ex) {
        log.error("Failed to index document [{}] after retries: {}", document.getId(), ex.getMessage());
        meterRegistry.counter("railcab_search_index_operations_total",
                "model", document.getModel(), "outcome", "failure").increment();
        throw new SearchIndexUnavailableException("Search index is currently unavailable", ex);
    }

    @CircuitBreaker(name = "search-index", fallbackMethod = "findOneFallback")
    @Retry(name = "search-index")
    public SearchDocument findOne(String model, String modelKey) {
        return searchIndexPort.findOne(model, modelKey);
    }

    @SuppressWarnings("unused")
    private SearchDocument findOneFallback(String model, String modelKey, Exception ex) {
        log.error("Failed to fetch document [{}:{}] after retries: {}", model, modelKey, ex.getMessage());
        throw new SearchIndexUnavailableException("Search index is currently unavailable", ex);
    }

    @CircuitBreaker(name = "search-index", fallbackMethod = "deleteFallback")
    @Retry(name = "search-index")
    public void delete(String model, String modelKey) {
        searchIndexPort.delete(model, modelKey);
    }

    @SuppressWarnings("unused")
    private void deleteFallback(String model, String modelKey, Exception ex) {
        log.error("Failed to delete document [{}:{}] after retries: {}", model, modelKey, ex.getMessage());
        throw new SearchIndexUnavailableException("Search index is currently unavailable", ex);
    }

    @CircuitBreaker(name = "search-index", fallbackMethod = "searchFallback")
    @Retry(name = "search-index")
    public Page<SearchDocument> search(SearchQuery query, Pageable pageable) {
        Page<SearchDocument> results = searchIndexPort.search(query, pageable);
        meterRegistry.counter("railcab_search_queries_total",
                "model", query.model().name(),
                "result", results.getTotalElements() == 0 ? "empty" : "hit").increment();
        return results;
    }

    @SuppressWarnings("unused")
    private Page<SearchDocument> searchFallback(SearchQuery query, Pageable pageable, Exception ex) {
        log.error("Search failed for model [{}] after retries: {}", query.model(), ex.getMessage());
        meterRegistry.counter("railcab_search_queries_total",
                "model", query.model().name(), "result", "error").increment();
        throw new SearchIndexUnavailableException("Search index is currently unavailable", ex);
    }

    public static String compositeId(String model, String modelKey) {
        return model.toLowerCase() + ":" + modelKey;
    }
}
