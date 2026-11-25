package com.heuristiq.identity.organization.legalprofile.adapter.in;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.organization.legalprofile.application.LegalProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An organization's legal profile: legal entity, registration, tax ids and addresses. Privileges
 * {@code ORGANIZATION_LEGAL_PROFILE_READ} / {@code _UPDATE} at the gateway; an organization's own staff reach only their
 * organization (an organization-wide caller or a SUPER_ADMIN reaches every one).
 */
@RestController
@RequestMapping("/api/v1/uam/organizations/{id}/legal-profile")
@Tag(name = "Organization legal profile", description = "An organization's legal entity, tax ids and addresses.")
public class LegalProfileController {

    private final LegalProfileUseCase legalProfiles;
    private final CommonRequestMapper requestMapper;

    public LegalProfileController(LegalProfileUseCase legalProfiles, CommonRequestMapper requestMapper) {
        this.legalProfiles = legalProfiles;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Get an organization's legal profile",
            description = "The saved profile with its version. An organization without one answers an empty profile (version null), not 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal profile returned"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Organization id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(LegalProfileErrors.translated(() -> {
            LegalProfileAccess.assertCanReach(id);
            return LegalProfileResponse.from(legalProfiles.view(id));
        })));
    }

    @Operation(summary = "Replace an organization's legal profile",
            description = "Replaces the whole profile (tax ids and addresses as sets) and the organization's legal name. Send the "
                    + "version read with the GET (null for the first save). Request payload is a LegalProfileRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile saved; the new state is returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed: message is a legalProfile.* key, errorCode the field"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "409", description = "legalProfile.staleVersion: the profile was changed since it was read"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(LegalProfileRequest.class)
    @PutMapping
    public ResponseEntity<CommonResponseObject> replace(@Parameter(description = "Organization id") @PathVariable String id,
                                                        @Valid @RequestBody CommonRequestObject commonRequest,
                                                        Authentication authentication) {
        LegalProfileRequest request = requestMapper.requireRequestObject(commonRequest, LegalProfileRequest.class);
        String actor = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(CommonResponseObject.success(LegalProfileErrors.translated(() -> {
            LegalProfileAccess.assertCanReach(id);
            return LegalProfileResponse.from(legalProfiles.replace(id, request.toDraft(), request.version(), actor));
        })));
    }
}
