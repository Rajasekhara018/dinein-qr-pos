package com.heuristiq.uam.search;

import java.util.List;
import java.util.Map;

/** Runs every {@link SearchIndexEnricher} over an entry's own fields, each seeing what the ones before added. */
public final class SearchIndexEnrichment {

    private SearchIndexEnrichment() {
    }

    public static Map<String, Object> apply(List<SearchIndexEnricher> enrichers, SearchIndexable indexable, Map<String, Object> fields) {
        Map<String, Object> result = fields;
        for (SearchIndexEnricher enricher : enrichers) {
            result = enricher.enrich(indexable, result);
        }
        return result;
    }
}
