package com.heuristiq.identity.organization.signup;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public and unauthenticated. It lives under {@code /identity/auth/**} on purpose: that path is already open in UAM's
 * security and the gateway and carries the gateway's strict auth rate limit, and it is not in the privilege catalogue.
 * Read-only, and exposes only code and display name.
 */
@RestController
@RequestMapping("/api/v1/uam/identity/auth/signup-organizations")
@Tag(name = "Self-signup organizations (public)", description = "Organizations an applicant without a login may choose.")
public class PublicSelfSignupOrganizationController {

    private final SelfSignupOrganizationServiceUseCase service;

    public PublicSelfSignupOrganizationController(SelfSignupOrganizationServiceUseCase service) {
        this.service = service;
    }

    @Operation(summary = "List the organizations an applicant may choose for an application",
            description = "Only ACTIVE organizations with a current subscription to the application that opted in to self-signup, sorted by name. An unknown or malformed application code gives an empty list.")
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@RequestParam(name = "applicationCode", required = false) String applicationCode) {
        return ResponseEntity.ok(CommonResponseObject.success(
                service.list(new SelfSignupOrganizationServiceUseCase.ListInput(applicationCode)).organizations()));
    }
}
