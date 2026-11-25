package com.heuristiq.identity.permissions;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.OperationResult;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.dto.UpsertPermissionRequest;
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
@RequestMapping("/api/v1/uam/permissions")
@Tag(name = "Permissions", description = "Fine-grained permissions that can be granted to roles.")
public class PermissionController {
    private final PermissionServiceUseCase permissionService;
    private final CommonRequestMapper requestMapper;

    public PermissionController(PermissionServiceUseCase permissionService, CommonRequestMapper requestMapper) {
        this.permissionService = permissionService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Create a permission", description = "Creates a new permission. Request payload is an UpsertPermissionRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Permission created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Permission code already exists"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertPermissionRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertPermissionRequest request = requestMapper.requireRequestObject(commonRequest, UpsertPermissionRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                permissionService.create(new PermissionServiceUseCase.CreateInput(request)).permission()));
    }

    @Operation(summary = "Update a permission", description = "Replaces the mutable fields of an existing permission. Request payload is an UpsertPermissionRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Permission not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertPermissionRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Permission id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertPermissionRequest request = requestMapper.requireRequestObject(commonRequest, UpsertPermissionRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                permissionService.update(new PermissionServiceUseCase.UpdateInput(id, request)).permission()));
    }

    @Operation(summary = "Get a permission by id", description = "Fetches a single permission's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission found"),
            @ApiResponse(responseCode = "404", description = "Permission not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Permission id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                permissionService.get(new PermissionServiceUseCase.GetInput(id)).permission()));
    }

    @Operation(summary = "List permissions", description = "Returns a page of permissions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of permissions returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(CommonResponseObject.success(
                permissionService.list(new PermissionServiceUseCase.ListPageInput(page, size)).permissions()));
    }

    @Operation(summary = "Delete a permission", description = "Permanently removes a permission record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Permission deleted"),
            @ApiResponse(responseCode = "404", description = "Permission not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "Permission id") @PathVariable String id) {
        permissionService.delete(new PermissionServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }
}
