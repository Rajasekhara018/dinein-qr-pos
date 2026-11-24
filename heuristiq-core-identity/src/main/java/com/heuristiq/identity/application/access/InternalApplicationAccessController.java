package com.heuristiq.uam.application.access;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Service-to-service only: reachable with the shared {@code X-Rider-Service-Token} (see InternalServiceSecurityConfig),
 * never with an end-user token, and not exposed through the public gateway catalog.
 */
@RestController
@RequestMapping("/api/v1/uam/internal/application-access")
@Tag(name = "Application access (internal)", description = "Service-to-service calls used by onboarding.")
public class InternalApplicationAccessController {
    private final ApplicationUserProvisioningServiceUseCase provisioningService;
    private final CommonRequestMapper requestMapper;

    public InternalApplicationAccessController(ApplicationUserProvisioningServiceUseCase provisioningService,
                                               CommonRequestMapper requestMapper) {
        this.provisioningService = provisioningService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Find or create an account and grant it application access",
            description = "Looks the person up by mobile or email, creates the account when there is none, and grants access to the application. An existing account keeps its credentials. Safe to repeat.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found or created and access granted"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the account type is not allowed"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong service token"),
            @ApiResponse(responseCode = "404", description = "Application or the named organization not found"),
            @ApiResponse(responseCode = "409", description = "Mobile and email belong to different accounts, or the named organization is not active or does not use the application"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(ProvisionApplicationUserRequest.class)
    @PostMapping("/provision-user")
    public ResponseEntity<CommonResponseObject> provisionUser(@Valid @RequestBody CommonRequestObject commonRequest) {
        ProvisionApplicationUserRequest request = requestMapper.requireRequestObject(commonRequest, ProvisionApplicationUserRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                provisioningService.provision(new ApplicationUserProvisioningServiceUseCase.ProvisionInput(request))));
    }

    @Operation(summary = "Look for an account by phone number and email",
            description = "Read only. Says whether an account uses the phone number or the email, which of the two match, whether it is active and its roles; userId is null when nothing is found or the two belong to different accounts. Nothing is created or changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The answer; found is false when there is no such account"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong service token"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(LookupAccountRequest.class)
    @PostMapping("/accounts/lookup")
    public ResponseEntity<CommonResponseObject> lookupAccount(@Valid @RequestBody CommonRequestObject commonRequest) {
        LookupAccountRequest request = requestMapper.requireRequestObject(commonRequest, LookupAccountRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(provisioningService.lookup(
                new ApplicationUserProvisioningServiceUseCase.LookupInput(request.mobile(), request.email()))));
    }

    @Operation(summary = "Give an account without a password a temporary one",
            description = "For an active customer account that has no password yet (it signed up with a one-time code): sets a random one-time password, marked temporary so it must be changed at first sign-in, and returns it once so the application can email it. An account that already has a password is left untouched and the answer carries no password. Back-office accounts are refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Done; temporaryPassword is null when the account already had a password"),
            @ApiResponse(responseCode = "400", description = "Validation failed, the account is not active, or it is a back-office account"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong service token"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(IssueTemporaryPasswordRequest.class)
    @PostMapping("/issue-temporary-password")
    public ResponseEntity<CommonResponseObject> issueTemporaryPassword(@Valid @RequestBody CommonRequestObject commonRequest) {
        IssueTemporaryPasswordRequest request = requestMapper.requireRequestObject(commonRequest, IssueTemporaryPasswordRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(provisioningService.issueTemporaryPassword(
                new ApplicationUserProvisioningServiceUseCase.IssueTemporaryPasswordInput(request.userId()))));
    }
}
