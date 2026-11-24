package com.heuristiq.search.provider.elasticsearch;

import org.springframework.data.elasticsearch.core.query.Criteria;

/** {@code GE} -- shared by numeric and date fields, whose range behavior is identical. */
final class GreaterThanOrEqualCriteriaStrategy implements OperatorCriteriaStrategy {

    @Override
    public Criteria toCriteria(String field, Object value) {
        return new Criteria(field).greaterThanEqual(value);
    }
}
