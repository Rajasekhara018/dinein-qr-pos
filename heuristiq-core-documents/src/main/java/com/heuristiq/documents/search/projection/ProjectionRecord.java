package com.heuristiq.documents.search.projection;

import java.util.Map;

/**
 * One row of a projection: its key in the model and its fields (the public DTO under {@code payload}, plus the slot
 * values the catalogue names). Rider_SearchAPI adds the organization on receipt, so it is never carried here.
 */
public record ProjectionRecord(String modelKey, Map<String, Object> fields) {
}
