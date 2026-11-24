package com.heuristiq.search.provider.elasticsearch;

import org.springframework.data.elasticsearch.core.query.Criteria;

/** {@code LIKE} -- text fields only. */
final class ContainsCriteriaStrategy implements OperatorCriteriaStrategy {

    @Override
    public Criteria toCriteria(String field, Object value) {
        return new Criteria(field).contains((String) value);
    }
}
