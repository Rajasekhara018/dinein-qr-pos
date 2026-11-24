package com.heuristiq.search.provider.solr;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.provider.SearchIndexPort;
import com.heuristiq.search.query.SearchQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Apache Solr implementation of {@link SearchIndexPort}, a sample of a second search engine behind the same port: it
 * stores each document as a flat Solr document and answers queries through Solr's JSON HTTP API (no client library).
 * Select it with {@code railcab.search.provider=solr} and point {@code railcab.search.solr.*} at a collection set up as
 * described in docs/solr-sample/README.md. Everything above the port (the service, both search APIs, the indexing
 * listener) is unchanged when it is selected.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.search", name = "provider", havingValue = "solr")
public class SolrSearchIndexProvider implements SearchIndexPort {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() { };

    private final RestClient client;
    private final SolrQueryTranslator translator;

    /** @param client a client whose base URL is the collection (see {@link SolrConfiguration}) */
    public SolrSearchIndexProvider(@Qualifier("solrRestClient") RestClient client, SolrQueryTranslator translator) {
        this.client = client;
        this.translator = translator;
    }

    @Override
    public SearchDocument index(SearchDocument document) {
        // commit=true makes the document searchable at once, which the platform's read-your-writes screens expect.
        client.post().uri(SolrConstants.UPDATE_COMMIT_PATH).contentType(MediaType.APPLICATION_JSON)
                .body(List.of(SolrDocumentMapper.toSolr(document))).retrieve().toBodilessEntity();
        return document;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SearchDocument findOne(String model, String modelKey) {
        Map<String, Object> answer = client.get()
                .uri(uri -> uri.path(SolrConstants.REALTIME_GET_PATH).queryParam(SolrConstants.ID, compositeId(model, modelKey)).build())
                .retrieve().body(JSON_OBJECT);
        Object doc = answer == null ? null : answer.get(SolrConstants.REALTIME_GET_DOC);
        return doc instanceof Map<?, ?> found ? SolrDocumentMapper.fromSolr((Map<String, Object>) found) : null;
    }

    @Override
    public void delete(String model, String modelKey) {
        client.post().uri(SolrConstants.UPDATE_COMMIT_PATH).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(SolrConstants.DELETE_COMMAND, Map.of(SolrConstants.ID, compositeId(model, modelKey)))).retrieve().toBodilessEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<SearchDocument> search(SearchQuery query, Pageable pageable) {
        SolrQueryTranslator.SolrQuery solr = translator.translate(query, pageable.getSort());
        // Every value goes in as a URI variable: a Solr query is full of braces, quotes and colons that must be
        // encoded as data, never read as URI-template syntax.
        Map<String, Object> vars = new java.util.LinkedHashMap<>();
        vars.put("q", solr.q());
        vars.put("start", pageable.getOffset());
        vars.put("rows", pageable.getPageSize());
        Map<String, Object> answer = client.get().uri(uri -> {
            uri.path("/select").queryParam("wt", "json").queryParam("q", "{q}")
                    .queryParam("start", "{start}").queryParam("rows", "{rows}");
            if (!solr.sort().isEmpty()) {
                uri.queryParam("sort", "{sort}");
                vars.put("sort", solr.sort());
            }
            for (int i = 0; i < solr.filters().size(); i++) {
                uri.queryParam("fq", "{fq" + i + "}");
                vars.put("fq" + i, solr.filters().get(i));
            }
            return uri.build(vars);
        }).retrieve().body(JSON_OBJECT);

        Map<String, Object> response = answer == null ? Map.of() : (Map<String, Object>) answer.getOrDefault("response", Map.of());
        List<SearchDocument> content = new ArrayList<>();
        for (Object doc : (List<Object>) response.getOrDefault("docs", List.of())) {
            content.add(SolrDocumentMapper.fromSolr((Map<String, Object>) doc));
        }
        long total = ((Number) response.getOrDefault("numFound", (long) content.size())).longValue();
        return new PageImpl<>(content, pageable, total);
    }

    private static String compositeId(String model, String modelKey) {
        return model.toLowerCase() + ":" + modelKey;
    }
}
