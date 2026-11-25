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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An organization's branches (restaurant locations). Privileges {@code POS_BRANCH_*} at the gateway; a branch-scoped caller
 * sees only the branches they are assigned to, and anything else answers "not found".
 */
@RestController
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@RequestMapping("/api/v1/uam/organizations/{orgId}/branches")
@Tag(name = "POS branches", description = "An organization's branches.")
public class BranchController {

    private final PosOrganizationUseCase pos;
    private final CommonRequestMapper requestMapper;
    private final PosCallers callers;

    BranchController(PosOrganizationUseCase pos, CommonRequestMapper requestMapper, PosCallers callers) {
        this.pos = pos;
        this.requestMapper = requestMapper;
        this.callers = callers;
    }

    @Operation(summary = "List an organization's branches", description = "A branch-scoped caller gets only their own branches.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branches returned"),
            @ApiResponse(responseCode = "404", description = "Organization not found or not reachable"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Organization id") @PathVariable String orgId) {
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return pos.branches(caller, orgId).stream().map(BranchResponse::from).toList();
        })));
    }

    @Operation(summary = "Get a branch")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branch returned"),
            @ApiResponse(responseCode = "404", description = "Not found or not reachable"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                    @Parameter(description = "Branch id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return BranchResponse.from(pos.branch(caller, orgId, id));
        })));
    }

    @Operation(summary = "Create a branch", description = "The answer carries version 1. Request payload is a BranchRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branch created"),
            @ApiResponse(responseCode = "400", description = "Validation failed: message is a pos.* key, errorCode the field"),
            @ApiResponse(responseCode = "404", description = "Organization not found or not reachable"),
            @ApiResponse(responseCode = "409", description = "pos.branch.codeTaken"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(BranchRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                       @Valid @RequestBody CommonRequestObject commonRequest) {
        BranchRequest request = requestMapper.requireRequestObject(commonRequest, BranchRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return BranchResponse.from(pos.createBranch(caller, orgId, request.toDraft()));
        })));
    }

    @Operation(summary = "Change a branch", description = "Send the version read with the GET. Request payload is a BranchRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Branch saved; the new state is returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed: message is a pos.* key, errorCode the field"),
            @ApiResponse(responseCode = "404", description = "Not found or not reachable"),
            @ApiResponse(responseCode = "409", description = "pos.staleVersion or another clash with the current state"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(BranchRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                       @Parameter(description = "Branch id") @PathVariable String id,
                                                       @Valid @RequestBody CommonRequestObject commonRequest) {
        BranchRequest request = requestMapper.requireRequestObject(commonRequest, BranchRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return BranchResponse.from(pos.updateBranch(caller, orgId, id, request.toDraft(),
                    PosErrors.requireVersion(request.version())));
        })));
    }
}
