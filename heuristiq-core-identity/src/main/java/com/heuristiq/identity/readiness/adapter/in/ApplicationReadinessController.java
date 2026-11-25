package com.heuristiq.identity.readiness.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.readiness.application.ApplicationReadinessUseCase;

/**
 * Readiness of the session organization's applications (docs/APPLICATION_READINESS.md). Thin: parses, delegates, maps.
 * The organization is the session's; the gateway protects both endpoints with their own privileges.
 */
@RestController
@RequestMapping(ReadinessWebConstants.BASE_PATH)
@Tag(name = "Application readiness", description = "Which setup steps of each application are done, missing or blocking, for the session organization.")
public class ApplicationReadinessController {

    private final ApplicationReadinessUseCase readiness;

    public ApplicationReadinessController(ApplicationReadinessUseCase readiness) {
        this.readiness = readiness;
    }

    @Operation(summary = "Readiness summary of the organization's applications",
            description = "One line per catalogue application linked to the session organization: status, required steps passed, "
                    + "warnings and the next step. Results are reused for a short time; refresh=true re-checks now. "
                    + "Privilege APPLICATION_READINESS_LIST.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary returned (an empty list when the organization has no applications)"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping(ReadinessWebConstants.SUMMARY_PATH)
    public ResponseEntity<CommonResponseObject> summary(
            @Parameter(description = "Re-check now instead of reusing a recent result")
            @RequestParam(name = ReadinessWebConstants.PARAM_REFRESH, defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(CommonResponseObject.success(
                readiness.summaries(refresh).stream().map(ReadinessSummaryResponse::from).toList()));
    }

    @Operation(summary = "Readiness guide of one application",
            description = "Every setup step of the application for the session organization, in phase and order, with its status, "
                    + "severity, message key, fix route, prerequisites and whether it is locked; plus the overall status and the "
                    + "next step. A service that does not answer makes its steps UNKNOWN (never PASS). refresh=true re-checks now. "
                    + "Privilege APPLICATION_READINESS_READ.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Readiness returned"),
            @ApiResponse(responseCode = "404", description = "The application is not linked to the session organization"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping(ReadinessWebConstants.APPLICATION_PATH)
    public ResponseEntity<CommonResponseObject> application(
            @Parameter(description = "Catalogue application code, e.g. PASUPU_KUMKUMA_MATRIMONY_APP") @PathVariable String code,
            @Parameter(description = "Re-check now instead of reusing a recent result")
            @RequestParam(name = ReadinessWebConstants.PARAM_REFRESH, defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(CommonResponseObject.success(
                ApplicationReadinessResponse.from(readiness.readiness(code, refresh))));
    }
}
