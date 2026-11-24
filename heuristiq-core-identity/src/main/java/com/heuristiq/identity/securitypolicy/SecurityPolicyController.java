package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.common.RequestObjectType;
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
@RequestMapping("/api/v1/uam/security-policies")
@Tag(name = "Security Policies", description = "Configurable password/login/session/MFA/API/account security rules, optionally scoped to a tenant and/or application.")
public class SecurityPolicyController {
    private final SecurityPolicyServiceUseCase securityPolicyService;
    private final CommonRequestMapper requestMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private EffectiveSecurityPolicyResolver resolver;
    @org.springframework.beans.factory.annotation.Autowired
    private SecurityPolicyAccess policyAccess;

    @GetMapping("/effective")
    public ResponseEntity<CommonResponseObject> effective(@RequestParam(required = false) String tenantId,
                                          @RequestParam(required = false) String applicationId) {
        String tenant = org.springframework.util.StringUtils.hasText(tenantId) ? tenantId.trim() : null;
        policyAccess.require("READ", tenant);
        return ResponseEntity.ok(CommonResponseObject.success(resolver.resolve(tenant, applicationId)));
    }

    @GetMapping("/templates")
    public ResponseEntity<CommonResponseObject> templates(@RequestParam(required = false) String tenantId) {
        policyAccess.require("READ", org.springframework.util.StringUtils.hasText(tenantId) ? tenantId.trim() : null);
        return ResponseEntity.ok(CommonResponseObject.success(SecurityPolicyTemplates.all()));
    }

    public SecurityPolicyController(SecurityPolicyServiceUseCase securityPolicyService, CommonRequestMapper requestMapper) {
        this.securityPolicyService = securityPolicyService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Create a security policy", description = "Creates a new security policy. Request payload is an UpsertSecurityPolicyRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Security policy created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Security policy already exists for this tenant/application"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertSecurityPolicyRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertSecurityPolicyRequest request = requestMapper.requireRequestObject(commonRequest, UpsertSecurityPolicyRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                securityPolicyService.create(new SecurityPolicyServiceUseCase.CreateInput(request)).policy()));
    }

    @Operation(summary = "Update a security policy", description = "Replaces the mutable fields of an existing security policy. Request payload is an UpsertSecurityPolicyRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security policy updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Security policy not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertSecurityPolicyRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Security policy id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertSecurityPolicyRequest request = requestMapper.requireRequestObject(commonRequest, UpsertSecurityPolicyRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                securityPolicyService.update(new SecurityPolicyServiceUseCase.UpdateInput(id, request)).policy()));
    }

    @Operation(summary = "Get a security policy by id", description = "Fetches a single security policy's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security policy found"),
            @ApiResponse(responseCode = "404", description = "Security policy not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Security policy id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                securityPolicyService.get(new SecurityPolicyServiceUseCase.GetInput(id)).policy()));
    }

    @Operation(summary = "List security policies", description = "Returns a page of security policies.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of security policies returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
                               @Parameter(description = "Filter by policy name (partial, case-insensitive)") @RequestParam(required = false) String name,
                               @Parameter(description = "Filter by exact tenant id") @RequestParam(required = false) String tenantId) {
        return ResponseEntity.ok(CommonResponseObject.success(
                securityPolicyService.list(new SecurityPolicyServiceUseCase.ListPageInput(page, size, name, tenantId)).policies()));
    }

    @Operation(summary = "Delete a security policy", description = "Permanently removes a security policy record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Security policy deleted"),
            @ApiResponse(responseCode = "404", description = "Security policy not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "Security policy id") @PathVariable String id) {
        securityPolicyService.delete(new SecurityPolicyServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }
}
