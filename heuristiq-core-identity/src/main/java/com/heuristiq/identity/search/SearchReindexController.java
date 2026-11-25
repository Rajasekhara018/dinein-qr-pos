package com.heuristiq.identity.search;

import com.heuristiq.identity.common.CommonResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/** Starts and monitors the re-index of identity records into the search index. Needs the privileges in privilege.json. */
@RestController
@RequestMapping("/api/v1/uam/search/reindex")
@Tag(name = "Search index", description = "Re-publishes existing identity records to the search index.")
public class SearchReindexController {

    private final SearchReindexServiceUseCase reindexService;

    public SearchReindexController(SearchReindexServiceUseCase reindexService) {
        this.reindexService = reindexService;
    }

    @Operation(summary = "Start a search re-index",
            description = "Re-publishes every user, role, tenant, application, API client and security policy "
                    + "(or only the given model) to the search index, in the background. One run at a time.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Re-index started"),
            @ApiResponse(responseCode = "400", description = "Unknown model"),
            @ApiResponse(responseCode = "409", description = "A re-index is already running; its progress is returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping
    public ResponseEntity<CommonResponseObject> start(
            @Parameter(description = "Only this model: USER, ROLE, TENANT, APPLICATION, API_CLIENT or SECURITY_POLICY")
            @RequestParam(required = false) String model) {
        SearchReindexServiceUseCase.StartOutput out =
                reindexService.start(new SearchReindexServiceUseCase.StartInput(Optional.ofNullable(model)));
        HttpStatus status = out.started() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(CommonResponseObject.success(out.report()));
    }

    @Operation(summary = "Search re-index progress", description = "The current or last re-index run: state, times and per-model counts.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Progress returned")})
    @GetMapping
    public CommonResponseObject status() {
        return CommonResponseObject.success(reindexService.status());
    }
}
