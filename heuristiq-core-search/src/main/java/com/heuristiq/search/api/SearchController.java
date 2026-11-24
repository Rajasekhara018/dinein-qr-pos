package com.heuristiq.search.api;

import com.heuristiq.search.api.dto.SearchRequest;
import com.heuristiq.search.common.CommonConstants;
import com.heuristiq.search.common.dto.CommonResponseObject;
import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.scope.TenantScopedSearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SearchApiConstants.BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Search", description = "Platform-wide search over indexed rides, drivers, vehicles, support tickets, refund requests, and users.")
public class SearchController {

    private final TenantScopedSearch search;

    @Operation(summary = "Search indexed documents",
            description = "Runs a SearchQuery (model + optional AND/OR condition group) against the shared "
                    + "Elasticsearch index and returns a page of matching documents. Field names in the query "
                    + "must be one of the generic slot names (text1..text15/amount1..amount5/number1..number5/"
                    + "date1..date5/datetime1..datetime5) -- see each producer service's SearchDocumentMapper "
                    + "for what a given model's slots mean.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search executed (possibly zero results)"),
            @ApiResponse(responseCode = "400", description = "Validation failure or unsupported operator/field combination (VALIDATION_ERROR)"),
            @ApiResponse(responseCode = "503", description = "Elasticsearch is currently unavailable (SEARCH_INDEX_UNAVAILABLE)")
    })
    @PostMapping
    public ResponseEntity<CommonResponseObject> search(@Valid @RequestBody SearchRequest request) {
        if (request.query().model().name().startsWith("FRANCHISE_") || request.query().model().name().startsWith("WEB_")) {
            return ResponseEntity.status(403).body(CommonResponseObject.failure("Use the model-specific GetAll API for franchise records", "FORBIDDEN"));
        }
        Pageable pageable = PageRequest.of(request.page(), request.size());
        Page<SearchDocument> results = search.search(request.query(), pageable);
        return ResponseEntity.ok(CommonResponseObject.success(results));
    }

    @Operation(summary = "Get one indexed document",
            description = "Fetches a single indexed document by its model type and the owning entity's id, "
                    + "as assigned by that entity's source-of-truth service (e.g. BookingsAPI's ride id for "
                    + "model=RIDE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "404", description = "No document indexed for this model/modelKey"),
            @ApiResponse(responseCode = "503", description = "Elasticsearch is currently unavailable (SEARCH_INDEX_UNAVAILABLE)")
    })
    @GetMapping("/{model}/{modelKey}")
    public ResponseEntity<CommonResponseObject> getOne(
            @Parameter(description = "Indexed entity type") @PathVariable SearchModel model,
            @Parameter(description = "The owning entity's id in its source-of-truth service") @PathVariable String modelKey) {
        if (model.name().startsWith("FRANCHISE_") || model.name().startsWith("WEB_")) {
            return ResponseEntity.status(403).body(CommonResponseObject.failure("Use the model-specific GetAll API for franchise records", "FORBIDDEN"));
        }
        SearchDocument document = search.findOne(model.name(), modelKey);
        if (document == null) {
            return ResponseEntity.status(404)
                    .body(CommonResponseObject.failure("No " + model + " document indexed for id " + modelKey, CommonConstants.ERROR_NOT_FOUND));
        }
        return ResponseEntity.ok(CommonResponseObject.success(document));
    }
}
