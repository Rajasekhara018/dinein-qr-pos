package com.heuristiq.search.elastic;

import com.heuristiq.search.api.SearchApiConstants;
import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.SearchQuery;
import com.heuristiq.search.scope.TenantRequiredException;
import com.heuristiq.search.scope.TenantScopedSearch;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "getall" index API: list or search the indexed records of one model with one request shape, answered in the
 * {@code {requestType, success, errors, object}} envelope. It adds nothing to what {@code /api/v1/search} can do except
 * the request and response format (nested AND/OR groups, {@code criteria1}, {@code sort}, string page sizes).
 * Authorised by the gateway like every other search call; it holds no business rules. A model whose records belong to an
 * organization (users, roles, API clients, security policies) returns only the caller's own organization's records: the
 * organization comes from the verified session, never from the request.
 *
 * <p>Engine independence: everything here (request, mapper, response) speaks only the engine-neutral
 * {@code SearchQuery} and {@code SearchIndexService}. Which database answers is decided by the {@code SearchIndexPort}
 * implementation selected with {@code railcab.search.provider}; moving to another searchable database means adding one
 * port implementation, with no change to this API or its clients.
 */
@RestController
@RequestMapping(SearchApiConstants.ELASTIC_PATH)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Index (getall)", description = "List or search the indexed records of a model: POST /api/v1/search/elastic/{model}/getall.")
public class ElasticController {

    private final TenantScopedSearch search;

    @Operation(summary = "List or search the records of a model",
            description = "Without searchQuery every record of the model is listed; with it, the AND/OR filter tree is applied. "
                    + "criteria1 is 0 for AND and 1 for OR; a group may hold conditions (field, operator, value) and other groups. "
                    + "Operators: EQ, NE, GE, GT, LE, LT, LIKE, IN. A date value is ISO-8601 or epoch milliseconds. "
                    + "Sorted by sort.field1.. (default createdAt newest first).")
    @PostMapping("/{model}/getall")
    public ResponseEntity<ElasticResponse> getAll(
            @Parameter(description = "Model: application, user, role, tenant, matrimony-profile, ... (case, dashes and underscores ignored)")
            @PathVariable String model,
            @RequestBody ElasticGetAllRequest request) {
        String type = request.requestType() == null || request.requestType().isBlank() ? ElasticConstants.REQUEST_TYPE_LIST : request.requestType();
        if (!ElasticConstants.REQUEST_TYPE_LIST.equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Unsupported requestType: " + type);
        }
        SearchModel searchModel = ElasticQueryMapper.modelOf(model);
        SearchQuery query = ElasticQueryMapper.toSearchQuery(searchModel, request);
        Pageable pageable = ElasticQueryMapper.toPageable(request);
        Page<SearchDocument> found = search.search(query, pageable);
        return ResponseEntity.ok(ElasticResponse.ok(ElasticConstants.REQUEST_TYPE_LIST, found.map(ElasticDocument::of)));
    }

    @ExceptionHandler({IllegalArgumentException.class, java.time.format.DateTimeParseException.class})
    ResponseEntity<ElasticResponse> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ElasticResponse.failed(ElasticConstants.REQUEST_TYPE_LIST, ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<ElasticResponse> unreadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ElasticResponse.failed(ElasticConstants.REQUEST_TYPE_LIST, "The request body could not be read"));
    }

    @ExceptionHandler(TenantRequiredException.class)
    ResponseEntity<ElasticResponse> noOrganization(TenantRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ElasticResponse.failed(ElasticConstants.REQUEST_TYPE_LIST, ex.getMessage()));
    }

    @ExceptionHandler(SearchIndexUnavailableException.class)
    ResponseEntity<ElasticResponse> unavailable(SearchIndexUnavailableException ex) {
        log.error("getall failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ElasticResponse.failed(ElasticConstants.REQUEST_TYPE_LIST, "Search index is currently unavailable"));
    }
}
