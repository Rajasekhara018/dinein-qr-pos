package com.heuristiq.search.provider.elasticsearch;

import org.springframework.data.elasticsearch.core.query.Criteria;

/** {@code LE} -- shared by numeric and date fields, whose range behavior is identical. */
final class LessThanOrEqualCriteriaStrategy implements OperatorCriteriaStrategy {

    @Override
    public Criteria toCriteria(String field, Object value) {
        return new Criteria(field).lessThanEqual(value);
    }
}
