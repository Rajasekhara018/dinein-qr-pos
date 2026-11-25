package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.pos.application.PosCaller;
import com.heuristiq.identity.pos.application.PosOrganizationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The people who work at a branch. Privileges {@code POS_BRANCH_STAFF_READ} / {@code _UPDATE} at the gateway; replacing the
 * staff is for tenant-wide callers only, anyone else answers "not found".
 */
@RestController
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@RequestMapping("/api/v1/uam/organizations/{orgId}/branches/{id}/staff")
@Tag(name = "POS branch staff", description = "The people assigned to a branch.")
public class BranchStaffController {

    private final PosOrganizationUseCase pos;
    private final CommonRequestMapper requestMapper;
    private final PosCallers callers;

    BranchStaffController(PosOrganizationUseCase pos, CommonRequestMapper requestMapper, PosCallers callers) {
        this.pos = pos;
        this.requestMapper = requestMapper;
        this.callers = callers;
    }

    @Operation(summary = "List a branch's staff")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Staff returned"),
            @ApiResponse(responseCode = "404", description = "Not found or not reachable"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                    @Parameter(description = "Branch id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return BranchStaffResponse.from(pos.branchStaff(caller, orgId, id));
        })));
    }

    @Operation(summary = "Replace a branch's staff",
            description = "Makes the sent users the complete staff. Request payload is a BranchStaffRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Staff saved; the new staff is returned"),
            @ApiResponse(responseCode = "400", description = "pos.staff.notMember: a user is not an active member of the organization"),
            @ApiResponse(responseCode = "404", description = "Not found or not reachable"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(BranchStaffRequest.class)
    @PutMapping
    public ResponseEntity<CommonResponseObject> replace(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                        @Parameter(description = "Branch id") @PathVariable String id,
                                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        BranchStaffRequest request = requestMapper.requireRequestObject(commonRequest, BranchStaffRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return BranchStaffResponse.from(pos.replaceBranchStaff(caller, orgId, id, request.toUserIds()));
        })));
    }
}
