package com.heuristiq.search.provider;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.query.SearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Port (hexagonal) for the underlying search/index engine. {@link com.heuristiq.search.service.SearchIndexService}
 * depends only on this interface -- never on Elasticsearch types directly -- so a different engine
 * (Solr, OpenSearch, ...) can be adopted by adding one new {@code @ConditionalOnProperty}-gated
 * implementation of this interface, without touching the service, controller, or the generic
 * {@link SearchQuery}/{@link SearchDocument} model. Selected via {@code railcab.search.provider}
 * (default {@code elasticsearch}), the same convention PaymentAPI/DMS use for their own
 * pluggable-backend switches.
 */
public interface SearchIndexPort {

    SearchDocument index(SearchDocument document);

    SearchDocument findOne(String model, String modelKey);

    void delete(String model, String modelKey);

    Page<SearchDocument> search(SearchQuery query, Pageable pageable);
}
