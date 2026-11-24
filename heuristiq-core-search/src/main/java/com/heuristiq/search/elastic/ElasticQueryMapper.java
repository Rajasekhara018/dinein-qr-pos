package com.heuristiq.search.elastic;

import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a getall request into the engine-agnostic {@link SearchQuery} and the page to read. Pure and stateless; every
 * problem with the request is an {@link IllegalArgumentException} the controller answers as a 400.
 */
public final class ElasticQueryMapper {

    private ElasticQueryMapper() {
    }

    /** The model named in the URL ({@code application}, {@code matrimony-profile}, ...), matched ignoring case, dashes and underscores. */
    public static SearchModel modelOf(String path) {
        String wanted = normalise(path);
        for (SearchModel model : SearchModel.values()) {
            if (normalise(model.name()).equals(wanted)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown model: " + path);
    }

    /** The filter of the request on the given model; no {@code searchQuery} (or an empty one) means "everything of the model". */
    public static SearchQuery toSearchQuery(SearchModel model, ElasticGetAllRequest request) {
        ElasticGetAllRequest.Node root = request.searchQuery() == null ? null : request.searchQuery().group();
        if (root == null || root.isCondition() || root.group() == null || root.group().isEmpty()) {
            return new SearchQuery(model, null);
        }
        return new SearchQuery(model, toGroup(root));
    }

    /** The page and sort of the request, with defaults and limits applied. */
    public static Pageable toPageable(ElasticGetAllRequest request) {
        int page = request.pageNo() == null ? 0 : request.pageNo();
        int size = request.pageSize() == null ? ElasticConstants.DEFAULT_PAGE_SIZE : request.pageSize();
        if (page < 0) {
            throw new IllegalArgumentException("pageNo must be zero or greater");
        }
        if (size < 1 || size > ElasticConstants.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + ElasticConstants.MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, toSort(request.sort()));
    }

    private static SearchGroup toGroup(ElasticGetAllRequest.Node node) {
        LogicOp logic = logicOf(node.criteria1());
        List<SearchCondition> conditions = new ArrayList<>();
        List<SearchGroup> groups = new ArrayList<>();
        for (ElasticGetAllRequest.Node child : node.group() == null ? List.<ElasticGetAllRequest.Node>of() : node.group()) {
            if (child.isCondition()) {
                conditions.add(new SearchCondition(child.field().trim(), operatorOf(child.operator()), child.value()));
            } else if (child.group() != null && !child.group().isEmpty()) {
                groups.add(toGroup(child));
            }
        }
        return new SearchGroup(logic, conditions, groups);
    }

    private static LogicOp logicOf(Integer criteria) {
        int code = criteria == null ? ElasticConstants.CRITERIA_AND : criteria;
        return switch (code) {
            case ElasticConstants.CRITERIA_AND -> LogicOp.AND;
            case ElasticConstants.CRITERIA_OR -> LogicOp.OR;
            default -> throw new IllegalArgumentException("criteria1 must be 0 (AND) or 1 (OR), not " + code);
        };
    }

    private static Operator operatorOf(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("operator is required on a condition");
        }
        try {
            return Operator.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported operator: " + text);
        }
    }

    private static Sort toSort(ElasticGetAllRequest.Sort sort) {
        List<Sort.Order> orders = new ArrayList<>();
        if (sort != null) {
            addOrder(orders, sort.field1(), sort.directionField1());
            addOrder(orders, sort.field2(), sort.directionField2());
            addOrder(orders, sort.field3(), sort.directionField3());
        }
        if (orders.isEmpty()) {
            orders.add(Sort.Order.desc(ElasticConstants.DEFAULT_SORT_FIELD));
        }
        return Sort.by(orders);
    }

    private static void addOrder(List<Sort.Order> orders, String field, String direction) {
        if (field == null || field.isBlank()) {
            return;
        }
        String name = field.trim();
        if (!ElasticConstants.SORTABLE_FIELD.matcher(name).matches()) {
            throw new IllegalArgumentException("Cannot sort on " + name);
        }
        // The logical slot name only: how an engine sorts a text slot (Elasticsearch needs its keyword sub-field) is the
        // provider's business, so this mapper stays engine-neutral.
        String column = name;
        boolean descending = direction != null && direction.trim().equalsIgnoreCase(ElasticConstants.SORT_DESC);
        if (direction != null && !direction.isBlank() && !descending && !direction.trim().equalsIgnoreCase(ElasticConstants.SORT_ASC)) {
            throw new IllegalArgumentException("Sort direction must be ASC or DESC, not " + direction);
        }
        orders.add(descending ? Sort.Order.desc(column) : Sort.Order.asc(column));
    }

    private static String normalise(String name) {
        return name == null ? "" : name.replaceAll("[-_\\s]", "").toLowerCase(Locale.ROOT);
    }
}
