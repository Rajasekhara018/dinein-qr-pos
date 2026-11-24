package com.heuristiq.uam.role;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.dto.UpsertRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam/roles")
@Tag(name = "Roles", description = "Roles that bundle a set of permissions and can be assigned to users/groups.")
public class RoleController {
    private final UamRoleServiceUseCase roleService;
    private final CommonRequestMapper requestMapper;

    public RoleController(UamRoleServiceUseCase roleService, CommonRequestMapper requestMapper) {
        this.roleService = roleService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Create a role", description = "Creates a new role. Request payload is an UpsertRoleRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Role code already exists"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertRoleRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertRoleRequest request = requestMapper.requireRequestObject(commonRequest, UpsertRoleRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                roleService.create(new UamRoleServiceUseCase.CreateInput(request)).role()));
    }

    @Operation(summary = "Update a role", description = "Replaces the mutable fields of an existing role, including its permission set. Request payload is an UpsertRoleRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertRoleRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Role id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertRoleRequest request = requestMapper.requireRequestObject(commonRequest, UpsertRoleRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                roleService.update(new UamRoleServiceUseCase.UpdateInput(id, request)).role()));
    }

    @Operation(summary = "Get a role by id", description = "Fetches a single role's details, including its permission set.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role found"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Role id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                roleService.get(new UamRoleServiceUseCase.GetInput(id)).role()));
    }

    @Operation(summary = "List roles", description = "Returns a page of roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of roles returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
                               @Parameter(description = "Filter by exact role code") @RequestParam(required = false) String code,
                               @Parameter(description = "Filter by role name (partial, case-insensitive)") @RequestParam(required = false) String name) {
        return ResponseEntity.ok(CommonResponseObject.success(
                roleService.list(new UamRoleServiceUseCase.ListPageInput(page, size, code, name)).roles()));
    }

    @Operation(summary = "Delete a role", description = "Permanently removes a role record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role deleted"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "Role id") @PathVariable String id) {
        roleService.delete(new UamRoleServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }
}
