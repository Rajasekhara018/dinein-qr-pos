package com.heuristiq.uam.organization;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.enums.UamEnums.TenantType;
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
@RequestMapping("/api/v1/uam/organizations")
@Tag(name = "Organizations", description = "Tenant organizations that own users, applications, and policies in this multi-tenant service.")
public class OrganizationController {
    private final TenantServiceUseCase organizationService;
    private final CommonRequestMapper requestMapper;

    public OrganizationController(TenantServiceUseCase organizationService, CommonRequestMapper requestMapper) {
        this.organizationService = organizationService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Create an organization", description = "Creates a new tenant organization. Request payload is an UpsertOrganizationRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organization created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Organization code already exists"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertOrganizationRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertOrganizationRequest request = requestMapper.requireRequestObject(commonRequest, UpsertOrganizationRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                organizationService.create(new TenantServiceUseCase.CreateInput(request)).tenant()));
    }

    @Operation(summary = "Update an organization", description = "Replaces the mutable fields of an existing organization. Request payload is an UpsertOrganizationRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Organization updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertOrganizationRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Organization id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertOrganizationRequest request = requestMapper.requireRequestObject(commonRequest, UpsertOrganizationRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                organizationService.update(new TenantServiceUseCase.UpdateInput(id, request)).tenant()));
    }

    @Operation(summary = "Get an organization by id", description = "Fetches a single organization's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Organization found"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Organization id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                organizationService.get(new TenantServiceUseCase.GetInput(id)).tenant()));
    }

    @Operation(summary = "List organizations", description = "Returns a page of tenant organizations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of organizations returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
                               @Parameter(description = "Filter by exact organization code") @RequestParam(required = false) String code,
                               @Parameter(description = "Filter by organization name (partial, case-insensitive)") @RequestParam(required = false) String name,
                               @Parameter(description = "Filter by exact organization type") @RequestParam(required = false) TenantType type,
                               @Parameter(description = "Filter by exact organization status") @RequestParam(required = false) TenantStatus status) {
        return ResponseEntity.ok(CommonResponseObject.success(
                organizationService.list(new TenantServiceUseCase.ListPageInput(page, size, code, name, type, status)).tenants()));
    }

    @Operation(summary = "Delete an organization", description = "Permanently removes an organization record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Organization deleted"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "Organization id") @PathVariable String id) {
        organizationService.delete(new TenantServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }
}
