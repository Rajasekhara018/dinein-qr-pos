package com.heuristiq.uam.organization.membership;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The signed-in person's own organizations, for choosing which one to work in. Under the identity self-service path, so any
 * authenticated caller may read their own and only their own. To work in another one, refresh the session with the
 * {@value TenancyConstants#ORGANIZATION_HEADER} header.
 */
@RestController
@RequestMapping("/api/v1/uam/identity/me/organizations")
@Tag(name = "My organizations", description = "The organizations the signed-in account belongs to.")
public class MyOrganizationsController {
    private final TenantMembershipServiceUseCase service;

    public MyOrganizationsController(TenantMembershipServiceUseCase service) {
        this.service = service;
    }

    @Operation(summary = "My organizations", description = "Organizations the caller is an active or paused member of, oldest membership first.")
    @GetMapping
    public ResponseEntity<CommonResponseObject> mine(Authentication authentication) {
        return ResponseEntity.ok(CommonResponseObject.success(service.organizationsOf(authentication.getName())));
    }
}
