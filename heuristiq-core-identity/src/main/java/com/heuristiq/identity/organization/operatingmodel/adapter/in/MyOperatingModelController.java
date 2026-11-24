package com.heuristiq.uam.organization.operatingmodel.adapter.in;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.organization.operatingmodel.application.OperatingModelUseCase;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelException;
import com.heuristiq.uam.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operating model of the signed-in session's organization, so the web apps can show only the menus that fit how it runs
 * (for example no franchise proposals in a DIRECT organization). Under the identity self-service path, which is not in the
 * privilege catalogue: any signed-in person may ask, and only about the organization of their own token (never one named in
 * the request). Showing or hiding a menu is only a hint; the endpoints behind it keep their own checks.
 */
@RestController
@RequestMapping("/api/v1/uam/identity/me/operating-model")
@Tag(name = "My organization's operating model", description = "How the signed-in session's organization runs: DIRECT, FRANCHISE or HYBRID.")
public class MyOperatingModelController {

    private final OperatingModelUseCase operatingModels;

    public MyOperatingModelController(OperatingModelUseCase operatingModels) {
        this.operatingModels = operatingModels;
    }

    @Operation(summary = "My organization's operating model today",
            description = "DIRECT, FRANCHISE or HYBRID today, for the organization of the caller's token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model returned"),
            @ApiResponse(responseCode = "400", description = "The session names no organization"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> mine() {
        return ResponseEntity.ok(CommonResponseObject.success(OperatingModelErrors.translated(() -> {
            String tenantId = TenantContext.get();
            if (tenantId == null || tenantId.isBlank()) {
                throw OperatingModelException.invalid(OperatingModelConstants.ERROR_NO_SESSION_ORGANIZATION);
            }
            return OperatingModelResponse.OnDay.from(operatingModels.modelOn(tenantId, null));
        })));
    }
}
