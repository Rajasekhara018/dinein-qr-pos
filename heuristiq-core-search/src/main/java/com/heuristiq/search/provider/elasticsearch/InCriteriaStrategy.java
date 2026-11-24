package com.heuristiq.search.provider.elasticsearch;

import org.springframework.data.elasticsearch.core.query.Criteria;

import java.util.Arrays;

/** {@code IN} -- text fields only; splits the comma-separated value into a keyword membership check. */
final class InCriteriaStrategy implements OperatorCriteriaStrategy {

    @Override
    public Criteria toCriteria(String field, Object value) {
        return new Criteria(field).in((Object[]) splitValues((String) value));
    }

    private String[] splitValues(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).toArray(String[]::new);
    }
}
