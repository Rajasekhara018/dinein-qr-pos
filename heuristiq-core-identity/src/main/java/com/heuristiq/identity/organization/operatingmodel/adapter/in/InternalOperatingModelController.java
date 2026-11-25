package com.heuristiq.identity.organization.operatingmodel.adapter.in;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (shared service token, see InternalServiceSecurityConfig); not in the gateway catalogue. Booking
 * services ask which model an organization runs on the day of a sale, to choose its operator.
 */
@RestController
@RequestMapping("/api/v1/uam/internal/organizations/{tenantId}/operating-model")
@Tag(name = "Organization operating model (internal)", description = "Service-to-service lookup of an organization's operating model.")
public class InternalOperatingModelController {

    private final OperatingModelUseCase operatingModels;

    public InternalOperatingModelController(OperatingModelUseCase operatingModels) {
        this.operatingModels = operatingModels;
    }

    @Operation(summary = "The operating model of an organization on a day",
            description = "DIRECT, FRANCHISE or HYBRID on the given day (default: today in the organization's time zone). Idempotent read.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model returned"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong service token"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> modelOn(
            @Parameter(description = "Organization id (the token's tenantId)") @PathVariable String tenantId,
            @Parameter(description = "Day, YYYY-MM-DD; default today") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(CommonResponseObject.success(OperatingModelErrors.translated(
                () -> OperatingModelResponse.OnDay.from(operatingModels.modelOn(tenantId, date)))));
    }
}
