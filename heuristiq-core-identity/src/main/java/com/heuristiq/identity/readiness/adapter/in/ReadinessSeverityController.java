package com.heuristiq.identity.readiness.adapter.in;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.readiness.adapter.in.GovernanceRequests.SeverityRequest;
import com.heuristiq.identity.readiness.application.ReadinessSeverityUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Required / Recommended choice per readiness step, for the session organization. Thin: parse, delegate, map. Privileges:
 * APPLICATION_READINESS_SEVERITY_UPDATE (PUT), APPLICATION_READINESS_SEVERITY_DELETE (reset one step),
 * APPLICATION_READINESS_SEVERITY_ALL_DELETE (reset all). Each call returns the fresh readiness of the application.
 */
@RestController
@RequestMapping(ReadinessWebConstants.BASE_PATH)
@Tag(name = "Application readiness severity", description = "Choose which setup steps of an application are required or only recommended.")
public class ReadinessSeverityController {

    private final ReadinessSeverityUseCase severity;
    private final CommonRequestMapper requestMapper;

    public ReadinessSeverityController(ReadinessSeverityUseCase severity, CommonRequestMapper requestMapper) {
        this.severity = severity;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Make a step required or recommended",
            description = "Sets one step of the application to BLOCK (required) or WARN (recommended) for the session organization. "
                    + "Making a step required also makes the steps it needs required; making it recommended also makes the steps "
                    + "that need it recommended. Returns the fresh readiness (same shape as the readiness GET).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saved; fresh readiness returned"),
            @ApiResponse(responseCode = "400", description = "Invalid severity (READINESS_SEVERITY_INVALID)"),
            @ApiResponse(responseCode = "404", description = "Application not linked to the organization, or unknown step (READINESS_STEP_NOT_FOUND)"),
            @ApiResponse(responseCode = "409", description = "The step is locked (READINESS_SEVERITY_LOCKED)")
    })
    @RequestObjectType(SeverityRequest.class)
    @PutMapping(ReadinessWebConstants.SEVERITY_PATH)
    public ResponseEntity<CommonResponseObject> change(
            @Parameter(description = "Catalogue application code") @PathVariable String code,
            @Valid @RequestBody CommonRequestObject commonRequest) {
        SeverityRequest request = requestMapper.requireRequestObject(commonRequest, SeverityRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                ApplicationReadinessResponse.from(severity.change(code, request.stepId(), request.severity()))));
    }

    @Operation(summary = "Reset one step to its default",
            description = "Sets the step back to its code default for the session organization; steps linked to it by the "
                    + "dependency rule also go back to their defaults when that is consistent. Returns the fresh readiness.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset; fresh readiness returned"),
            @ApiResponse(responseCode = "404", description = "Application not linked to the organization, or unknown step"),
            @ApiResponse(responseCode = "409", description = "The step is locked (READINESS_SEVERITY_LOCKED)")
    })
    @DeleteMapping(ReadinessWebConstants.SEVERITY_STEP_PATH)
    public ResponseEntity<CommonResponseObject> reset(
            @Parameter(description = "Catalogue application code") @PathVariable String code,
            @Parameter(description = "Readiness step id") @PathVariable String stepId) {
        return ResponseEntity.ok(CommonResponseObject.success(ApplicationReadinessResponse.from(severity.reset(code, stepId))));
    }

    @Operation(summary = "Reset every step of the application to its default",
            description = "Removes all severity choices of the session organization for this application. Returns the fresh readiness.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset; fresh readiness returned"),
            @ApiResponse(responseCode = "404", description = "Application not linked to the organization")
    })
    @DeleteMapping(ReadinessWebConstants.SEVERITY_PATH)
    public ResponseEntity<CommonResponseObject> resetAll(@Parameter(description = "Catalogue application code") @PathVariable String code) {
        return ResponseEntity.ok(CommonResponseObject.success(ApplicationReadinessResponse.from(severity.resetAll(code))));
    }
}
