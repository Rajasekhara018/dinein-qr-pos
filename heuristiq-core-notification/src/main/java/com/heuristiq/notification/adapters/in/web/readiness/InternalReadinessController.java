package com.railcab.nms.adapters.in.web.readiness;

import com.railcab.nms.application.usecase.readiness.NmsReadinessService;
import com.railcab.nms.shared.CommonResponseObject;
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
 * Internal readiness facts of NMS for Rider_UAM's application readiness combiner (docs/APPLICATION_READINESS.md). Reachable
 * only with a service credential ({@code X-Rider-Service-Token} or a SERVICE-role JWT, see
 * {@code NmsInternalServiceAuthFilter}); the internal NMS API key the gateway adds to user traffic does NOT open it. Not in
 * the privilege catalog on purpose. Read-only.
 */
@RestController
@Tag(name = "Internal: readiness", description = "Application readiness facts for Rider_UAM (service-to-service only)")
public class InternalReadinessController {

    private final NmsReadinessService readinessService;

    public InternalReadinessController(NmsReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @Operation(summary = "NMS readiness of one application for one organization",
            description = "Check NMS_TEMPLATES_EXIST: active templates (the organization's own or shared) for the application's "
                    + "events. Facts only; UNKNOWN when it could not be evaluated, NOT_APPLICABLE for a code that is not a "
                    + "business application.")
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
            throw new IllegalArgumentException(ReadinessWebConstants.MSG_PARAMS_REQUIRED);
        }
        return ResponseEntity.ok(CommonResponseObject.success(
                ReadinessResponse.from(readinessService.evaluate(applicationCode, tenantId))));
    }
}
