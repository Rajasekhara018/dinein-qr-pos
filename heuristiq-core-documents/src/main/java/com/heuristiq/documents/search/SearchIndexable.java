package com.heuristiq.documents.search;

import java.util.Map;

/**
 * Implemented by domain entities that get indexed into the shared RailCab search platform.
 * Mirrors the legacy touidx/touweb pattern where each domain model owns a
 * {@code @JsonIgnore getSearchObject()} method that projects itself into the generic search
 * document shape -- here split into the model tag, the id, and the slot-keyed field map
 * (text1../amount1../date1../datetime1..) that {@link SearchEventPublisher} serializes onto the
 * shared Redis Stream that 2026_RailCab_SearchAPI consumes.
 */
public interface SearchIndexable {

    /** One of 2026_RailCab_SearchAPI's SearchModel enum values, e.g. "DOCUMENT". */
    String getSearchModel();

    /** This entity's id in its own source-of-truth store -- becomes the search document's modelKey. */
    String getSearchModelKey();

    /** Slot-keyed field map (text1../amount1../number1../date1../datetime1..), nulls allowed. */
    Map<String, Object> getSearchIndexFields();
}
