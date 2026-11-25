package com.heuristiq.identity.search;

import java.util.Map;

/**
 * Adds index fields that cannot come from the entity alone (they depend on other records), for example the applications
 * a user may open. The search index writer calls it just before publishing, off the request thread.
 */
public interface SearchIndexEnricher {

    /** Returns the fields to publish: the entity's own, plus anything this enricher adds for it. */
    Map<String, Object> enrich(SearchIndexable indexable, Map<String, Object> fields);

    /** Whether {@link #onRecordSaved} cares about this kind of saved record. */
    default boolean followsUp(Object saved) {
        return false;
    }

    /** Called after a saved record that may change what {@link #enrich} adds to other records (an access grant). */
    default void onRecordSaved(Object saved) {
    }
}
