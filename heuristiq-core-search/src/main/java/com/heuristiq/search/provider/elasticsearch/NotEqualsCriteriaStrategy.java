package com.heuristiq.search.provider.elasticsearch;

import org.springframework.data.elasticsearch.core.query.Criteria;

/** {@code NE} -- identical for text, numeric and date fields, so one instance serves all three. */
final class NotEqualsCriteriaStrategy implements OperatorCriteriaStrategy {

    @Override
    public Criteria toCriteria(String field, Object value) {
        return new Criteria(field).not().is(value);
    }
}
