package com.heuristiq.search.query;

/**
 * Comparison operators supported by the search query DSL. Which operators apply to a given
 * {@link SearchCondition} depends on its field's type prefix ({@code text}/{@code amount}/
 * {@code number}/{@code date}/{@code datetime}) -- see the active {@code SearchIndexPort}
 * implementation's query translator (e.g. {@code ElasticsearchQueryTranslator}).
 */
public enum Operator {
    EQ,
    NE,
    GE,
    GT,
    LE,
    LT,
    LIKE,
    IN,
    /** The field has no value (the condition's value is ignored): records that belong to nobody, e.g. shared ones. */
    EMPTY
}
