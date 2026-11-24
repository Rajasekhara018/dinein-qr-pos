package com.heuristiq.uam.refreshToken;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.refreshToken.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.heuristiq.uam.biometric.application.BiometricRevocationUseCase;
import com.heuristiq.uam.biometric.application.BiometricRevocations;
import com.heuristiq.uam.biometric.domain.BiometricRevocationReason;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam")
@Tag(name = "Refresh Tokens", description = "Administrative management of active local refresh tokens issued to principals (users/clients).")
public class RefreshTokenController {
    private final RefreshTokenServiceUseCase refreshTokenService;
    private final ObjectProvider<BiometricRevocationUseCase> biometricRevocation;

    /** Without biometric revocation (tests that do not need it). */
    public RefreshTokenController(RefreshTokenServiceUseCase refreshTokenService) {
        this(refreshTokenService, null);
    }

    @Autowired
    public RefreshTokenController(RefreshTokenServiceUseCase refreshTokenService,
                                  ObjectProvider<BiometricRevocationUseCase> biometricRevocation) {
        this.refreshTokenService = refreshTokenService;
        this.biometricRevocation = biometricRevocation;
    }

    @Operation(summary = "List a principal's active refresh tokens", description = "Returns every currently-active local refresh token issued to the given principal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active tokens returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/principals/{principalId}/refresh-tokens")
    public CommonResponseObject listActive(@Parameter(description = "Principal (user or client) id") @PathVariable String principalId) {
        return CommonResponseObject.success(
                refreshTokenService.listActiveForPrincipal(
                        new RefreshTokenServiceUseCase.ListActiveForPrincipalInput(principalId)).tokens());
    }

    @Operation(summary = "Revoke a refresh token", description = "Immediately revokes a single refresh token, invalidating future refresh attempts with it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token revoked"),
            @ApiResponse(responseCode = "404", description = "Token not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/refresh-tokens/{tokenId}")
    public CommonResponseObject revoke(@Parameter(description = "Refresh token id") @PathVariable String tokenId) {
        refreshTokenService.revoke(new RefreshTokenServiceUseCase.RevokeInput(tokenId));
        return CommonResponseObject.success(OperationResult.completed("REVOKE", "REFRESH_TOKEN", tokenId));
    }

    @Operation(summary = "Revoke all refresh tokens for a principal", description = "Immediately revokes every active refresh token issued to the given principal, e.g. to force a full logout everywhere, and every fingerprint / Face ID device of a user principal (which could otherwise sign straight back in).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All tokens for the principal revoked"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/principals/{principalId}/refresh-tokens")
    public CommonResponseObject revokeAll(@Parameter(description = "Principal (user or client) id") @PathVariable String principalId) {
        // Admin "log out everywhere": a device key would sign the person straight back in, so it ends too (no-op for an API client).
        BiometricRevocations.revokeAll(biometricRevocation, principalId, BiometricRevocationReason.LOGOUT_ALL);
        refreshTokenService.revokeAllForPrincipal(new RefreshTokenServiceUseCase.RevokeAllForPrincipalInput(principalId));
        return CommonResponseObject.success(OperationResult.completed("REVOKE_ALL", "REFRESH_TOKEN", principalId));
    }
}
