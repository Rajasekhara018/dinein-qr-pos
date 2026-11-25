package com.heuristiq.notification.adapters.in.web.search;

import com.heuristiq.notification.adapters.in.web.WebApiConstants;

import com.heuristiq.notification.search.SearchReindexUseCase;
import com.heuristiq.notification.shared.CommonResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts and monitors the re-index of notification templates into the search index. Needs the privileges in privilege.json. */
@RestController
@RequestMapping(WebApiConstants.SEARCH_REINDEX_PATH)
@RequiredArgsConstructor
@Tag(name = "Search index", description = "Re-publishes existing notification templates to the search index.")
public class SearchReindexController {

    private final SearchReindexUseCase reindexService;

    @PostMapping
    @Operation(summary = "Start a search re-index",
            description = "Re-publishes every active notification template to the search index, in the background. One run at a time.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Re-index started"),
            @ApiResponse(responseCode = "409", description = "A re-index is already running; its progress is returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> start() {
        SearchReindexUseCase.StartOutput out = reindexService.start();
        HttpStatus status = out.started() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(CommonResponseObject.success(out.report()));
    }

    @GetMapping
    @Operation(summary = "Search re-index progress", description = "The current or last re-index run: state, times and counts.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Progress returned")})
    public ResponseEntity<CommonResponseObject> status() {
        return ResponseEntity.ok(CommonResponseObject.success(reindexService.status()));
    }
}
