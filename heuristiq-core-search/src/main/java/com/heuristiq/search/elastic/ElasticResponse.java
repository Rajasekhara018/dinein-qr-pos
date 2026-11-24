package com.heuristiq.search.elastic;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The getall answer: the request type, whether it worked, the errors when it did not, and the page of records.
 *
 * @param object the page (content plus paging figures) on success, absent on failure
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElasticResponse(String requestType, boolean success, List<String> errors, Object object) {

    public static ElasticResponse ok(String requestType, Object page) {
        return new ElasticResponse(requestType, true, List.of(), page);
    }

    public static ElasticResponse failed(String requestType, String error) {
        return new ElasticResponse(requestType, false, List.of(error), null);
    }
}
