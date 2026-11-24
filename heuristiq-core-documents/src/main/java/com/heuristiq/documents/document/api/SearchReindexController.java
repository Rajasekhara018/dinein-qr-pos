package com.railcab.dms.document.api;

import com.railcab.dms.common.dto.CommonResponseObject;
import com.railcab.dms.search.SearchReindexUseCase;
import com.railcab.dms.security.DmsSecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts and monitors the re-index of documents into the search index. Needs the privileges in privilege.json. */
@RestController
@RequestMapping("/api/v1/dms/search/reindex")
@Tag(name = "Search index", description = "Re-publishes existing documents to the search index.")
public class SearchReindexController {

    private final SearchReindexUseCase reindexService;

    public SearchReindexController(SearchReindexUseCase reindexService) {
        this.reindexService = reindexService;
    }

    @PostMapping
    @Operation(summary = "Start a search re-index",
            description = "Re-publishes every document that is not deleted to the search index, then the signed-in organization's document types and lifecycle documents (WEB_DMS_DOCUMENT_TYPE, WEB_DMS_LIFECYCLE_DOCUMENT) through the search projection, in the background. One run at a time.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Re-index started"),
            @ApiResponse(responseCode = "400", description = "The session has no organization"),
            @ApiResponse(responseCode = "409", description = "A re-index is already running; its progress is returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<CommonResponseObject> start() {
        SearchReindexUseCase.StartOutput out = reindexService.start(DmsSecurityContext.currentPrincipal().tenantId());
        HttpStatus status = out.started() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(CommonResponseObject.success(out.report()));
    }

    @GetMapping
    @Operation(summary = "Search re-index progress", description = "The current or last re-index run: state, times and the counts per model; error names a model that did not finish.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Progress returned")})
    public ResponseEntity<CommonResponseObject> status() {
        return ResponseEntity.ok(CommonResponseObject.success(reindexService.status()));
    }
}
