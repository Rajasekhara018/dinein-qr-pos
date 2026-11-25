package com.heuristiq.identity.mfa;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.OperationResult;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.common.UamException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service MFA access — see {@link com.heuristiq.identity.users.UserProfileController}'s javadoc for the pattern.
 * {@link UserMfaService}'s id-based methods (get/delete/disable/verifyEnrollment) have no
 * ownership check, so every action here verifies {@code mfa.getUserId()} against the caller first.
 */
@RestController
@RequestMapping({"/api/v1/uam/users/me/mfa", "/api/v1/uam/identity/me/mfa"})
@Tag(name = "User MFA", description = "Self-service enrollment and management of the authenticated caller's own multi-factor-authentication methods.")
public class UserMfaProfileController {
    private final UserMfaServiceUseCase mfaService;
    private final CommonRequestMapper requestMapper;

    public UserMfaProfileController(UserMfaServiceUseCase mfaService, CommonRequestMapper requestMapper) {
        this.mfaService = mfaService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "List my MFA methods", description = "Returns every MFA method enrolled for the caller's own account. Requires authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA methods returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> listMine(Authentication authentication) {
        return ResponseEntity.ok(CommonResponseObject.success(
                mfaService.listByUser(new UserMfaServiceUseCase.ListByUserInput(authentication.getName())).mfas()));
    }

    @Operation(summary = "Start TOTP enrollment", description = "Begins TOTP (authenticator app) enrollment for the caller's own account, returning the secret/QR data needed to complete setup.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "TOTP enrollment started, secret/QR data returned"),
            @ApiResponse(responseCode = "409", description = "TOTP is already enabled for this account"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/totp/enroll")
    public ResponseEntity<CommonResponseObject> enrollTotp(Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                mfaService.enrollTotp(new UserMfaServiceUseCase.EnrollTotpInput(authentication.getName())).response()));
    }

    @Operation(summary = "Complete MFA enrollment", description = "Verifies the code from the authenticator app to activate a pending MFA enrollment. Rejects the request if the MFA method does not belong to the caller. Request payload is an MfaVerifyRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA method verified and activated"),
            @ApiResponse(responseCode = "400", description = "MFA method not found/not owned by the caller, or code incorrect"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(MfaVerifyRequest.class)
    @PostMapping("/{id}/verify")
    public ResponseEntity<CommonResponseObject> verifyMine(Authentication authentication, @Parameter(description = "MFA method id") @PathVariable String id,
                                            @Valid @RequestBody CommonRequestObject commonRequest) {
        assertOwned(authentication, id);
        MfaVerifyRequest request = requestMapper.requireRequestObject(commonRequest, MfaVerifyRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                mfaService.verifyEnrollment(new UserMfaServiceUseCase.VerifyEnrollmentInput(id, request.code())).mfa()));
    }

    @Operation(summary = "Disable my MFA method", description = "Disables one of the caller's own MFA methods without deleting its record. Rejects the request if it does not belong to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA method disabled"),
            @ApiResponse(responseCode = "400", description = "MFA method not found or not owned by the caller"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/{id}/disable")
    public ResponseEntity<CommonResponseObject> disableMine(Authentication authentication, @Parameter(description = "MFA method id") @PathVariable String id) {
        assertOwned(authentication, id);
        return ResponseEntity.ok(CommonResponseObject.success(
                mfaService.disable(new UserMfaServiceUseCase.DisableInput(id)).mfa()));
    }

    @Operation(summary = "Remove my MFA method", description = "Permanently removes one of the caller's own MFA methods. Rejects the request if it does not belong to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "MFA method removed"),
            @ApiResponse(responseCode = "400", description = "MFA method not found or not owned by the caller"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> removeMine(Authentication authentication, @Parameter(description = "MFA method id") @PathVariable String id) {
        assertOwned(authentication, id);
        mfaService.delete(new UserMfaServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }

    private void assertOwned(Authentication authentication, String mfaId) {
        UserMfa mfa = mfaService.get(new UserMfaServiceUseCase.GetInput(mfaId)).mfa();
        if (!authentication.getName().equals(mfa.getUserId())) {
            throw new UamException("MFA method not found");
        }
    }
}
