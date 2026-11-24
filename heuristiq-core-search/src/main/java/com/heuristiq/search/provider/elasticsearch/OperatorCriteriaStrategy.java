package com.heuristiq.search.provider.elasticsearch;

import org.springframework.data.elasticsearch.core.query.Criteria;

/**
 * Strategy (GoF) for turning one {@link com.heuristiq.search.query.Operator} into a Spring Data
 * Elasticsearch {@link Criteria} clause for a given field/value pair. {@link ElasticsearchQueryTranslator}
 * resolves the right implementation from a {@code Map<Operator, OperatorCriteriaStrategy>} instead
 * of switching on the operator directly.
 *
 * <p>{@code field} and {@code value} are whatever the caller has already resolved for its field
 * category before delegating -- e.g. text conditions pass the {@code .keyword}-suffixed field name
 * and the raw string value, numeric/date conditions pass the field name as-is and the
 * already-parsed {@code Double}/{@code Long}/{@code Instant} value.
 */
interface OperatorCriteriaStrategy {

    Criteria toCriteria(String field, Object value);
}
