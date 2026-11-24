package com.heuristiq.documents.readiness.adapter.in;

import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.readiness.application.DmsReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal readiness facts of DMS for Rider_UAM's application readiness combiner (docs/APPLICATION_READINESS.md).
 * Reachable only with a service credential ({@code X-Rider-Service-Token} or a SERVICE-role JWT, see
 * {@code InternalServiceAuthFilter}); the gateway refuses the whole {@code /api/v1/dms/internal/} prefix. Not in the
 * privilege catalog on purpose. Read-only.
 */
@RestController
@Tag(name = "Internal", description = "Application readiness facts for Rider_UAM.")
public class InternalReadinessController {

    private final DmsReadinessService readinessService;

    public InternalReadinessController(DmsReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @Operation(summary = "DMS readiness of one application for one organization",
            description = "Check DMS_DOCUMENT_TYPES_CONFIGURED (organization-wide: document types are not linked to an "
                    + "application). Facts only; UNKNOWN when it could not be evaluated, NOT_APPLICABLE for a code that is "
                    + "not a business application.")
    @ApiResponse(responseCode = "200", description = "The report")
    @ApiResponse(responseCode = "400", description = "applicationCode or tenantId missing or blank")
    @ApiResponse(responseCode = "401", description = "No valid service credential")
    @GetMapping(ReadinessWebConstants.READINESS_PATH)
    public ResponseEntity<CommonResponseObject> readiness(
            @Parameter(description = "Business application code, e.g. PASUPU_KUMKUMA_MATRIMONY_APP")
            @RequestParam(name = ReadinessWebConstants.PARAM_APPLICATION_CODE, required = false) String applicationCode,
            @Parameter(description = "Organization id")
            @RequestParam(name = ReadinessWebConstants.PARAM_TENANT_ID, required = false) String tenantId) {
        if (!StringUtils.hasText(applicationCode) || !StringUtils.hasText(tenantId)) {
            throw DmsException.badRequest(ReadinessWebConstants.MSG_PARAMS_REQUIRED);
        }
        return ResponseEntity.ok(CommonResponseObject.success(
                ReadinessResponse.from(readinessService.evaluate(applicationCode, tenantId))));
    }
}
