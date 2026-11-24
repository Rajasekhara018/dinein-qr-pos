package com.heuristiq.uam.organization.signup;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Service-to-service only (shared service token, see InternalServiceSecurityConfig); not in the gateway catalogue. */
@RestController
@RequestMapping("/api/v1/uam/internal/self-signup-organizations")
@Tag(name = "Self-signup organizations (internal)", description = "Service-to-service calls used by onboarding.")
public class InternalSelfSignupOrganizationController {

    private final SelfSignupOrganizationServiceUseCase service;

    public InternalSelfSignupOrganizationController(SelfSignupOrganizationServiceUseCase service) {
        this.service = service;
    }

    @Operation(summary = "List the eligible organizations of a self-signup application, with ids",
            description = "Same rules and order as the public list. An unknown or malformed application code gives an empty list.")
    @GetMapping
    public ResponseEntity<CommonResponseObject> eligible(@RequestParam(name = "applicationCode", required = false) String applicationCode) {
        return ResponseEntity.ok(CommonResponseObject.success(
                service.eligible(new SelfSignupOrganizationServiceUseCase.ListInput(applicationCode)).organizations()));
    }

    @Operation(summary = "Resolve an organization code to its id for a self-signup application",
            description = "Applies the same rules as the public list (ACTIVE, subscribed, opted in). Idempotent read.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eligible: id, code and name"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong service token"),
            @ApiResponse(responseCode = "404", description = "Unknown or not eligible for self-signup")
    })
    @GetMapping("/resolve")
    public ResponseEntity<CommonResponseObject> resolve(@RequestParam("applicationCode") String applicationCode,
                                                        @RequestParam("organizationCode") String organizationCode) {
        return ResponseEntity.ok(CommonResponseObject.success(
                service.resolve(new SelfSignupOrganizationServiceUseCase.ResolveInput(applicationCode, organizationCode)).organization()));
    }
}
