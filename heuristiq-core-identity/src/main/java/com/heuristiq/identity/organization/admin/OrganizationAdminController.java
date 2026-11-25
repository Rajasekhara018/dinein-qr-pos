package com.heuristiq.identity.organization.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/uam/organizations/{tenantId}/admin")
@Tag(name = "Organizations", description = "Organizations (tenants) of the platform.")
public class OrganizationAdminController {
    private final OrganizationAdminServiceUseCase service;
    private final CommonRequestMapper requestMapper;

    public OrganizationAdminController(OrganizationAdminServiceUseCase service, CommonRequestMapper requestMapper) {
        this.service = service;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Create an organization's administrator",
            description = "Finds or creates the administrator's account, makes it a member of the organization and gives it the organization's admin role. A new account gets a one-time password, returned once. The payload is a CreateOrganizationAdminRequest inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Administrator set up"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "409", description = "The mobile number and email belong to different accounts"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(CreateOrganizationAdminRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Parameter(description = "Organization id") @PathVariable String tenantId,
                                                       @Valid @RequestBody CommonRequestObject commonRequest) {
        CreateOrganizationAdminRequest request = requestMapper.requireRequestObject(commonRequest, CreateOrganizationAdminRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                service.provision(new OrganizationAdminServiceUseCase.ProvisionInput(tenantId, request))));
    }
}
