package com.heuristiq.search.provider.solr;

import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchFieldConstants;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates the engine-neutral {@link SearchQuery} into Solr's query syntax: one filter on the model plus, when the
 * query has a group, one filter holding the whole AND/OR tree (nested groups become parentheses). The meaning of every
 * operator matches the Elasticsearch provider: EQ / NE exact, LIKE substring, IN a comma-separated list, and the range
 * operators on amounts, numbers and dates.
 */
@Component
class SolrQueryTranslator {

    /** What the provider sends to Solr's select handler. */
    record SolrQuery(String q, List<String> filters, String sort) {
    }

    SolrQuery translate(SearchQuery query, Sort sort) {
        List<String> filters = new ArrayList<>();
        filters.add(SolrConstants.MODEL + ":" + quote(query.model().name()));
        if (query.group() != null && query.group().isNotEmpty()) {
            filters.add(group(query.group()));
        }
        return new SolrQuery(SolrConstants.MATCH_ALL, filters, sortClause(sort));
    }

    private String group(SearchGroup group) {
        List<String> parts = new ArrayList<>();
        group.conditions().forEach(condition -> parts.add(condition(condition)));
        group.groups().stream().filter(SearchGroup::isNotEmpty).forEach(nested -> parts.add(group(nested)));
        String glue = group.logic() == LogicOp.OR ? " OR " : " AND ";
        return "(" + String.join(glue, parts) + ")";
    }

    private String condition(SearchCondition condition) {
        String field = condition.field();
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Search condition field must not be blank");
        }
        if (condition.operator() == Operator.EMPTY) {
            boolean slot = SolrConstants.SLOT_FIELD.matcher(field).matches();
            return "(*:* -" + (slot ? solrField(field) : structural(field)) + ":[* TO *])";
        }
        if (field.startsWith(SearchFieldConstants.DATETIME_PREFIX) || field.startsWith(SearchFieldConstants.DATE_PREFIX)) {
            return comparable(field + SolrConstants.DATE_SUFFIX, condition, date(condition.value()));
        }
        if (field.startsWith(SearchFieldConstants.AMOUNT_PREFIX)) {
            return comparable(field + SolrConstants.DOUBLE_SUFFIX, condition, Double.toString(Double.parseDouble(condition.value())));
        }
        if (field.startsWith(SearchFieldConstants.NUMBER_PREFIX)) {
            return comparable(field + SolrConstants.LONG_SUFFIX, condition, Long.toString(Long.parseLong(condition.value())));
        }
        if (field.startsWith(SearchFieldConstants.TEXT_PREFIX)) {
            return text(field + SolrConstants.EXACT_SUFFIX, condition);
        }
        // Structural fields (model / modelKey / ownerKey): plain equality only.
        return structural(field) + ":" + quote(condition.value());
    }

    private String text(String field, SearchCondition condition) {
        String value = condition.value();
        return switch (condition.operator()) {
            case EQ -> field + ":" + quote(value);
            case NE -> "(*:* -" + field + ":" + quote(value) + ")";
            case LIKE -> field + ":*" + escapeWildcard(value) + "*";
            case IN -> field + ":(" + Arrays.stream(value.split(",")).map(String::trim).map(SolrQueryTranslator::quote)
                    .collect(Collectors.joining(" OR ")) + ")";
            default -> throw unsupported(field, condition.operator());
        };
    }

    private String comparable(String field, SearchCondition condition, String value) {
        return switch (condition.operator()) {
            case EQ -> field + ":" + value;
            case NE -> "(*:* -" + field + ":" + value + ")";
            case GE -> field + ":[" + value + " TO *]";
            case GT -> field + ":{" + value + " TO *]";
            case LE -> field + ":[* TO " + value + "]";
            case LT -> field + ":[* TO " + value + "}";
            default -> throw unsupported(field, condition.operator());
        };
    }

    private static String structural(String field) {
        return switch (field) {
            case SearchFieldConstants.MODEL -> SolrConstants.MODEL;
            case SearchFieldConstants.MODEL_KEY -> SolrConstants.MODEL_KEY;
            case SearchFieldConstants.OWNER_KEY -> SolrConstants.OWNER_KEY;
            case SearchFieldConstants.TENANT_ID -> SolrConstants.TENANT_ID;
            default -> throw new IllegalArgumentException("Unknown field " + field);
        };
    }

    /** The Solr sort parameter ("date1_dt desc,text3_s asc"); empty when nothing is sorted. */
    private String sortClause(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "";
        }
        return sort.stream()
                .map(order -> solrField(order.getProperty()) + " " + (order.isDescending() ? "desc" : "asc"))
                .collect(Collectors.joining(","));
    }

    /** The stored field behind a logical slot name; a text slot sorts on its exact-value field. */
    static String solrField(String slot) {
        if (slot.equals(SearchFieldConstants.MODEL_KEY)) {
            return SolrConstants.MODEL_KEY;
        }
        if (slot.startsWith(SearchFieldConstants.DATETIME_PREFIX) || slot.startsWith(SearchFieldConstants.DATE_PREFIX)
                || slot.equals(SearchFieldConstants.CREATED_AT) || slot.equals(SearchFieldConstants.UPDATED_AT)) {
            return slot + SolrConstants.DATE_SUFFIX;
        }
        if (slot.startsWith(SearchFieldConstants.AMOUNT_PREFIX)) {
            return slot + SolrConstants.DOUBLE_SUFFIX;
        }
        if (slot.startsWith(SearchFieldConstants.NUMBER_PREFIX)) {
            return slot + SolrConstants.LONG_SUFFIX;
        }
        if (slot.startsWith(SearchFieldConstants.TEXT_PREFIX)) {
            return slot + SolrConstants.EXACT_SUFFIX;
        }
        throw new IllegalArgumentException("Cannot sort on " + slot);
    }

    /** An ISO-8601 instant, or epoch milliseconds, as Solr's date text. */
    private static String date(String text) {
        String value = text == null ? "" : text.trim();
        Instant instant = value.matches(SearchFieldConstants.EPOCH_MILLIS_REGEX) ? Instant.ofEpochMilli(Long.parseLong(value)) : Instant.parse(value);
        return instant.toString();
    }

    /** A phrase in double quotes, with the quote and backslash escaped. */
    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A value inside a wildcard term: every character Solr's parser treats as syntax is escaped. */
    static String escapeWildcard(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c < 128) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static IllegalArgumentException unsupported(String field, Operator operator) {
        return new IllegalArgumentException("Operator " + operator + " is not supported for field " + field);
    }
}
