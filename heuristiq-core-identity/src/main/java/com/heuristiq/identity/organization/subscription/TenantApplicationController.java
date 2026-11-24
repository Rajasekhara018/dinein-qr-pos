package com.heuristiq.uam.organization.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/uam/organizations/{tenantId}/applications")
@Tag(name = "Organization applications", description = "Which registered applications an organization (tenant) may use.")
public class TenantApplicationController {
    private final TenantApplicationServiceUseCase service;
    private final CommonRequestMapper requestMapper;

    public TenantApplicationController(TenantApplicationServiceUseCase service, CommonRequestMapper requestMapper) {
        this.service = service;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "List an organization's applications", description = "Every subscription, cancelled ones included, with the application name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscriptions returned"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Organization id") @PathVariable String tenantId) {
        return ResponseEntity.ok(CommonResponseObject.success(
                service.list(new TenantApplicationServiceUseCase.ListInput(tenantId)).subscriptions()));
    }

    @Operation(summary = "Give an organization an application",
            description = "Turns on, or turns back on, the organization's use of the application. The payload is a SubscribeTenantApplicationRequest inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscribed"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the application is switched off"),
            @ApiResponse(responseCode = "404", description = "Organization or application not found"),
            @ApiResponse(responseCode = "409", description = "The organization has reached its application limit"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(SubscribeTenantApplicationRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> subscribe(Authentication authentication,
                                                          @Parameter(description = "Organization id") @PathVariable String tenantId,
                                                          @Valid @RequestBody CommonRequestObject commonRequest) {
        SubscribeTenantApplicationRequest request = requestMapper.requireRequestObject(commonRequest, SubscribeTenantApplicationRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(service.subscribe(
                new TenantApplicationServiceUseCase.SubscribeInput(tenantId, request, actor(authentication))).subscription()));
    }

    @Operation(summary = "Change an organization's use of an application", description = "Status, member limit, end date or note. A field left out is not changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "404", description = "The organization does not use this application"),
            @ApiResponse(responseCode = "409", description = "The organization has reached its application limit"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpdateTenantApplicationRequest.class)
    @PutMapping("/{applicationCode}")
    public ResponseEntity<CommonResponseObject> update(Authentication authentication,
                                                       @Parameter(description = "Organization id") @PathVariable String tenantId,
                                                       @Parameter(description = "Application code") @PathVariable String applicationCode,
                                                       @Valid @RequestBody CommonRequestObject commonRequest) {
        UpdateTenantApplicationRequest request = requestMapper.requireRequestObject(commonRequest, UpdateTenantApplicationRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(service.update(
                new TenantApplicationServiceUseCase.UpdateInput(tenantId, applicationCode, request, actor(authentication))).subscription()));
    }

    @Operation(summary = "Take an application away from an organization", description = "Marks the subscription cancelled; it is kept so history stays readable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled"),
            @ApiResponse(responseCode = "404", description = "The organization does not use this application"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{applicationCode}")
    public ResponseEntity<CommonResponseObject> cancel(Authentication authentication,
                                                       @Parameter(description = "Organization id") @PathVariable String tenantId,
                                                       @Parameter(description = "Application code") @PathVariable String applicationCode) {
        return ResponseEntity.ok(CommonResponseObject.success(service.cancel(
                new TenantApplicationServiceUseCase.CancelInput(tenantId, applicationCode, actor(authentication))).subscription()));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
