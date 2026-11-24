package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.provider.SearchIndexPort;
import com.heuristiq.search.query.SearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch implementation of {@link SearchIndexPort} -- the equivalent of touidx's
 * {@code ElasticSearchService}, minus its Solr fallback and the acquiring-domain-specific
 * {@code getCriteriaWithMerchantNo} special-casing (this platform's {@code ownerKey} field is the
 * generic replacement for that per-model partition filtering, applied by callers via a normal
 * {@code SearchCondition} rather than a hardcoded switch). Active by default; set
 * {@code railcab.search.provider} to anything else to swap it out for a different engine's port
 * implementation instead.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "railcab.search", name = "provider", havingValue = "elasticsearch", matchIfMissing = true)
public class ElasticsearchSearchIndexProvider implements SearchIndexPort {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchQueryTranslator queryTranslator;
    private volatile boolean tenantMappingReady;

    @Override
    public SearchDocument index(SearchDocument document) {
        ensureIndexExists();
        return ElasticsearchDocumentMapper.toDocument(elasticsearchOperations.save(ElasticsearchDocumentMapper.toStored(document)));
    }

    @Override
    public SearchDocument findOne(String model, String modelKey) {
        ensureIndexExists();
        return ElasticsearchDocumentMapper.toDocument(elasticsearchOperations.get(compositeId(model, modelKey), ElasticsearchDocument.class));
    }

    @Override
    public void delete(String model, String modelKey) {
        elasticsearchOperations.delete(compositeId(model, modelKey), ElasticsearchDocument.class);
    }

    @Override
    public Page<SearchDocument> search(SearchQuery query, Pageable pageable) {
        ensureIndexExists();
        CriteriaQuery criteriaQuery = queryTranslator.translate(query);
        criteriaQuery.setPageable(sortableOnKeywords(pageable));
        SearchHits<ElasticsearchDocument> hits = elasticsearchOperations.search(criteriaQuery, ElasticsearchDocument.class);
        List<SearchDocument> results = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ElasticsearchDocumentMapper::toDocument)
                .collect(Collectors.toList());
        return new PageImpl<>(results, pageable, hits.getTotalHits());
    }

    /**
     * A text slot is analysed (for LIKE), so Elasticsearch can only sort it on its exact-value keyword sub-field. Callers
     * name the plain slot; this adds the sub-field, keeping that detail inside the Elasticsearch provider.
     */
    static Pageable sortableOnKeywords(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(pageable.getSort().stream()
                .map(order -> order.getProperty().matches("text\\d+")
                        ? order.withProperty(order.getProperty() + com.heuristiq.search.query.SearchFieldConstants.KEYWORD_SUFFIX)
                        : order)
                .toList());
        return org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private static String compositeId(String model, String modelKey) {
        return model.toLowerCase() + ":" + modelKey;
    }

    private void ensureIndexExists() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(ElasticsearchDocument.class);
        if (!indexOps.exists()) {
            try {
                indexOps.createWithMapping();
            } catch (org.springframework.data.elasticsearch.UncategorizedElasticsearchException e) {
                // Two requests can both see no index and both create it; the loser's "already exists" is the state we wanted.
                if (!indexOps.exists()) {
                    throw e;
                }
            }
        }
        if (!tenantMappingReady) {
            synchronized (this) {
                if (!tenantMappingReady) {
                    // Upgrade existing indexes too: an oversized keyword must not reject the entire document.
                    java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
                    properties.put("tenantId", java.util.Map.of("type", "keyword"));
                    properties.put("payload", java.util.Map.of("type", "object", "enabled", false));
                    for (int slot = 1; slot <= 15; slot++) {
                        properties.put("text" + slot, java.util.Map.of("type", "text", "fields",
                                java.util.Map.of("keyword", java.util.Map.of("type", "keyword", "ignore_above", 8191))));
                    }
                    indexOps.putMapping(org.springframework.data.elasticsearch.core.document.Document.from(
                            java.util.Map.of("properties", properties)));
                    tenantMappingReady = true;
                }
            }
        }
    }
}
