package com.heuristiq.uam.organization.operatingmodel.adapter.in;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.organization.operatingmodel.adapter.in.OperatingModelRequests.ScheduleOperatingModelRequest;
import com.heuristiq.uam.organization.operatingmodel.application.OperatingModelUseCase;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An organization's operating model (DIRECT, FRANCHISE or HYBRID) and its scheduled changes. Privileges
 * {@code ORGANIZATION_OPERATING_MODEL_READ} / {@code _UPDATE} at the gateway; an organization's own staff reach only their
 * organization (a SUPER_ADMIN reaches every one).
 */
@RestController
@RequestMapping("/api/v1/uam/organizations/{id}/operating-model")
@Tag(name = "Organization operating model", description = "Whether an organization runs its sales itself (DIRECT), through franchises (FRANCHISE), or through franchises where they cover a place and itself elsewhere (HYBRID).")
public class OperatingModelController {

    private final OperatingModelUseCase operatingModels;
    private final CommonRequestMapper requestMapper;

    public OperatingModelController(OperatingModelUseCase operatingModels, CommonRequestMapper requestMapper) {
        this.operatingModels = operatingModels;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Get an organization's operating model",
            description = "The model in force today (in the organization's time zone) and every change, oldest first, with whether it has started.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operating model returned"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Organization id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(OperatingModelErrors.translated(() -> {
            OperatingModelAccess.assertCanReach(id);
            return OperatingModelResponse.from(operatingModels.view(id));
        })));
    }

    @Operation(summary = "Schedule an organization's operating-model changes",
            description = "Replaces every change that has not started yet with the given ones (oldest first); an empty list cancels them. "
                    + "Each change starts today or later, after the one before it, and differs from the model in force before it. Changes already in force "
                    + "are kept, so past sales keep their operator. Request payload is a ScheduleOperatingModelRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schedule saved; the new state is returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed or a change starts in the past"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "409", description = "Changes out of order, a change to the model already in force, or the schedule was changed meanwhile"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(ScheduleOperatingModelRequest.class)
    @PutMapping
    public ResponseEntity<CommonResponseObject> schedule(@Parameter(description = "Organization id") @PathVariable String id,
                                                         @Valid @RequestBody CommonRequestObject commonRequest,
                                                         Authentication authentication) {
        ScheduleOperatingModelRequest request = requestMapper.requireRequestObject(commonRequest, ScheduleOperatingModelRequest.class);
        List<OperatingModelPeriod> changes = request.scheduledChanges().stream()
                .map(c -> new OperatingModelPeriod(c.model(), c.effectiveFrom()))
                .toList();
        String actor = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(CommonResponseObject.success(OperatingModelErrors.translated(() -> {
            OperatingModelAccess.assertCanReach(id);
            return OperatingModelResponse.from(operatingModels.scheduleFutureChanges(id, changes, actor));
        })));
    }
}
