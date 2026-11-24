package com.heuristiq.search.elastic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A "getall" request: a list of indexed records, optionally filtered, sorted and paged. The same request serves both
 * the plain listing (no {@code searchQuery}) and a search.
 *
 * @param messageID   echoed by the client for tracing; not interpreted (the model comes from the URL)
 * @param requestType must be {@code LIST}
 * @param searchQuery optional filter tree
 * @param pageNo      zero-based page, default 0
 * @param pageSize    page size, default 10, at most 50 (a number or numeric text)
 * @param sort        up to three sort fields
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A listing or search of indexed records of one model.")
public record ElasticGetAllRequest(
        String messageID,
        String requestType,
        SearchQuery searchQuery,
        Integer pageNo,
        Integer pageSize,
        Sort sort) {

    /** The filter tree: one root group. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchQuery(Node group) {
    }

    /**
     * A node of the filter tree: either a group (it has {@code group} children and a {@code criteria1} of 0 = AND, 1 = OR)
     * or a condition (it has {@code field}, {@code operator} and {@code value}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Node(Integer criteria1, List<Node> group, String field, String operator, String value) {

        /** True for a condition (a field to compare), false for a group. */
        public boolean isCondition() {
            return field != null && !field.isBlank();
        }
    }

    /** Sort fields and directions (ASC or DESC); only the first field is required. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sort(
            String field1, String directionField1,
            String field2, String directionField2,
            String field3, String directionField3) {
    }
}
