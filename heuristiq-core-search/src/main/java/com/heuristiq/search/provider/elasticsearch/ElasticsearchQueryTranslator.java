package com.heuristiq.search.provider.elasticsearch;

import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchFieldConstants;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the platform's engine-agnostic {@link SearchQuery} DSL into a Spring Data Elasticsearch
 * {@link CriteriaQuery} -- Elasticsearch-specific, lives beside {@link ElasticsearchSearchIndexProvider}
 * rather than in the generic {@code query} package so a future Solr/OpenSearch provider can own its
 * own translation without this class in its way. Dispatches by field-name prefix the same way the
 * legacy touidx source this is based on did -- minus its "adjacent date-range param merging"
 * optimization: two independent range clauses on the same field inside an AND group are already
 * equivalent to one merged range clause under Elasticsearch's bool-query semantics, so there's no
 * need to detect and special-case adjacent GE/LE pairs.
 */
@Component
class ElasticsearchQueryTranslator {

    // EQ/NE behave identically regardless of field type, so the same strategy instance is reused
    // by both the text map and the numeric/date ("comparable") map below.
    private static final OperatorCriteriaStrategy EQUALS = new EqualsCriteriaStrategy();
    private static final OperatorCriteriaStrategy NOT_EQUALS = new NotEqualsCriteriaStrategy();

    // text* fields: EQ/NE/LIKE/IN only.
    private static final Map<Operator, OperatorCriteriaStrategy> TEXT_STRATEGIES = new EnumMap<>(Operator.class);

    // amount*/number*/date*/datetime* fields: EQ/NE/GE/GT/LE/LT only. numericCriteria and
    // dateCriteria dispatched on an identical set of operators before this refactor, so they share
    // one strategy map.
    private static final Map<Operator, OperatorCriteriaStrategy> COMPARABLE_STRATEGIES = new EnumMap<>(Operator.class);

    static {
        TEXT_STRATEGIES.put(Operator.EQ, EQUALS);
        TEXT_STRATEGIES.put(Operator.NE, NOT_EQUALS);
        TEXT_STRATEGIES.put(Operator.LIKE, new ContainsCriteriaStrategy());
        TEXT_STRATEGIES.put(Operator.IN, new InCriteriaStrategy());

        COMPARABLE_STRATEGIES.put(Operator.EQ, EQUALS);
        COMPARABLE_STRATEGIES.put(Operator.NE, NOT_EQUALS);
        COMPARABLE_STRATEGIES.put(Operator.GE, new GreaterThanOrEqualCriteriaStrategy());
        COMPARABLE_STRATEGIES.put(Operator.GT, new GreaterThanCriteriaStrategy());
        COMPARABLE_STRATEGIES.put(Operator.LE, new LessThanOrEqualCriteriaStrategy());
        COMPARABLE_STRATEGIES.put(Operator.LT, new LessThanCriteriaStrategy());
    }

    CriteriaQuery translate(SearchQuery query) {
        Criteria criteria = new Criteria(SearchFieldConstants.MODEL).is(query.model().name());
        if (query.group() != null && query.group().isNotEmpty()) {
            criteria = criteria.subCriteria(toGroupCriteria(query.group()));
        }
        return new CriteriaQuery(criteria);
    }

    /**
     * Spring Data's criteria chains are flat (no brackets), so only these shapes can be expressed: conditions joined
     * by one operator, and an AND group whose nested groups hang off it as sub-criteria. The tree is first
     * simplified (a nested group with the same operator, or a single condition, is merged into its parent), which
     * covers the filters clients and the organization scope produce; an OR group that still holds a nested group
     * cannot be expressed and is rejected instead of being answered wrongly.
     */
    private Criteria toGroupCriteria(SearchGroup group) {
        List<SearchCondition> conditions = new ArrayList<>(group.conditions());
        List<SearchGroup> nested = new ArrayList<>();
        collect(group, conditions, nested);
        Criteria head = null;
        for (SearchCondition condition : conditions) {
            Criteria part = toConditionCriteria(condition);
            if (head == null) {
                head = part;
            } else if (group.logic() == LogicOp.AND) {
                head = head.and(part);
            } else {
                head = head.or(part);
            }
        }
        if (nested.isEmpty()) {
            return head;
        }
        if (group.logic() == LogicOp.OR) {
            throw new IllegalArgumentException("An OR group cannot contain a nested group of conditions with another operator");
        }
        if (head == null) {
            // Every document has a model, so this element is always true; sub-criteria need a field-bearing parent.
            head = new Criteria(SearchFieldConstants.MODEL).exists();
        }
        for (SearchGroup child : nested) {
            head = head.subCriteria(toGroupCriteria(child));
        }
        return head;
    }

    /** Merges the children that can be merged into the parent's conditions and keeps the others as nested groups. */
    private void collect(SearchGroup group, List<SearchCondition> conditions, List<SearchGroup> nested) {
        for (SearchGroup child : group.groups()) {
            if (!child.isNotEmpty()) {
                continue;
            }
            boolean single = child.conditions().size() == 1 && child.groups().stream().noneMatch(SearchGroup::isNotEmpty);
            if (single || child.logic() == group.logic()) {
                conditions.addAll(child.conditions());
                collect(child, conditions, nested);
            } else {
                nested.add(child);
            }
        }
    }

    private Criteria toConditionCriteria(SearchCondition condition) {
        String field = condition.field();
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Search condition field must not be blank");
        }
        if (condition.operator() == Operator.EMPTY) {
            String column = field.startsWith(SearchFieldConstants.TEXT_PREFIX) ? field + SearchFieldConstants.KEYWORD_SUFFIX : field;
            return new Criteria(column).not().exists();
        }
        if (field.startsWith(SearchFieldConstants.DATETIME_PREFIX) || field.startsWith(SearchFieldConstants.DATE_PREFIX)) {
            return dateCriteria(field, condition);
        }
        if (field.startsWith(SearchFieldConstants.AMOUNT_PREFIX)) {
            return numericCriteria(field, condition, true);
        }
        if (field.startsWith(SearchFieldConstants.NUMBER_PREFIX)) {
            return numericCriteria(field, condition, false);
        }
        if (field.startsWith(SearchFieldConstants.TEXT_PREFIX)) {
            return textCriteria(field, condition);
        }
        // Structural fields (model/modelKey/ownerKey) -- plain equality only.
        return new Criteria(field).is(condition.value());
    }

    private Criteria textCriteria(String field, SearchCondition condition) {
        String keywordField = field + SearchFieldConstants.KEYWORD_SUFFIX;
        return resolve(TEXT_STRATEGIES, field, condition).toCriteria(keywordField, condition.value());
    }

    private Criteria numericCriteria(String field, SearchCondition condition, boolean decimal) {
        Object value = decimal ? Double.parseDouble(condition.value()) : Long.parseLong(condition.value());
        return resolve(COMPARABLE_STRATEGIES, field, condition).toCriteria(field, value);
    }

    private Criteria dateCriteria(String field, SearchCondition condition) {
        Instant value = parseInstant(condition.value());
        return resolve(COMPARABLE_STRATEGIES, field, condition).toCriteria(field, value);
    }

    /** An ISO-8601 instant, or epoch milliseconds (what clients of the legacy index API send for a date). */
    private static Instant parseInstant(String text) {
        String value = text == null ? "" : text.trim();
        if (value.matches(SearchFieldConstants.EPOCH_MILLIS_REGEX)) {
            return Instant.ofEpochMilli(Long.parseLong(value));
        }
        return Instant.parse(value);
    }

    private OperatorCriteriaStrategy resolve(Map<Operator, OperatorCriteriaStrategy> strategies, String field,
            SearchCondition condition) {
        OperatorCriteriaStrategy strategy = strategies.get(condition.operator());
        if (strategy == null) {
            throw unsupported(field, condition.operator());
        }
        return strategy;
    }

    private IllegalArgumentException unsupported(String field, Operator operator) {
        return new IllegalArgumentException("Operator " + operator + " is not supported for field " + field);
    }
}
