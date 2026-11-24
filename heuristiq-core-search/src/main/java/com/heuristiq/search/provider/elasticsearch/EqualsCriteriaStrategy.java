package com.heuristiq.search.provider.elasticsearch;

import org.springframework.data.elasticsearch.core.query.Criteria;

/** {@code EQ} -- identical for text, numeric and date fields, so one instance serves all three. */
final class EqualsCriteriaStrategy implements OperatorCriteriaStrategy {

    @Override
    public Criteria toCriteria(String field, Object value) {
        return new Criteria(field).is(value);
    }
}
