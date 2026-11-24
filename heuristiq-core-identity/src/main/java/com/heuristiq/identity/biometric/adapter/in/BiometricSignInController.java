package com.heuristiq.uam.biometric.adapter.in;

import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.biometric.application.BiometricUseCase;
import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public half of fingerprint / Face ID sign-in, under {@code /identity/auth/**} like password login (no token needed). The
 * organization and application are chosen exactly as for login ({@code X-Organization-Code}, {@code X-Application-Code}).
 */
@RestController
@RequestMapping(BiometricWebConstants.SIGN_IN_PATH)
@Tag(name = "Biometric sign-in", description = "Sign in with fingerprint / Face ID: challenge and verify.")
public class BiometricSignInController {

    private final BiometricUseCase biometric;
    private final CommonRequestMapper requestMapper;

    public BiometricSignInController(BiometricUseCase biometric, CommonRequestMapper requestMapper) {
        this.biometric = biometric;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Get a sign-in challenge",
            description = "A one-time challenge (base64 of 32 random bytes) valid for 60 seconds, for the installation to sign with its device key. "
                    + "Always answered, also for an installation that never enrolled. Request payload is BiometricRequests.Challenge.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "challengeId, challenge, expiresAt"),
            @ApiResponse(responseCode = "400", description = "Malformed installation id")
    })
    @RequestObjectType(BiometricRequests.Challenge.class)
    @PostMapping("/challenge")
    public ResponseEntity<CommonResponseObject> challenge(@Valid @RequestBody CommonRequestObject commonRequest) {
        BiometricRequests.Challenge request = requestMapper.requireRequestObject(commonRequest, BiometricRequests.Challenge.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                BiometricResponses.Challenge.of(BiometricErrors.translated(() -> biometric.challenge(request.installationId())))));
    }

    @Operation(summary = "Sign in with a device signature",
            description = "Checks the device key's signature over the challenge and signs the account in: the answer is the same as password login's. "
                    + "401 BIOMETRIC_SIGNIN_INVALID for any challenge, credential or signature problem; 401 BIOMETRIC_CREDENTIAL_REVOKED when this "
                    + "installation's credential was revoked or expired (switch the feature off); 429 TOO_MANY_ATTEMPTS after 5 failures in 15 minutes. "
                    + "Account and access refusals are those of login. Request payload is BiometricRequests.Verify.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in: LoginResponse"),
            @ApiResponse(responseCode = "400", description = "Validation failed or account not active (as login)"),
            @ApiResponse(responseCode = "401", description = "BIOMETRIC_SIGNIN_INVALID or BIOMETRIC_CREDENTIAL_REVOKED"),
            @ApiResponse(responseCode = "403", description = "No access to the organization or application (as login)"),
            @ApiResponse(responseCode = "429", description = "TOO_MANY_ATTEMPTS")
    })
    @RequestObjectType(BiometricRequests.Verify.class)
    @PostMapping("/verify")
    public ResponseEntity<CommonResponseObject> verify(
            @RequestHeader(value = AuthConstants.APPLICATION_CODE_HEADER, required = false) String applicationCode,
            @Valid @RequestBody CommonRequestObject commonRequest) {
        BiometricRequests.Verify request = requestMapper.requireRequestObject(commonRequest, BiometricRequests.Verify.class);
        BiometricUseCase.VerifyCommand command = new BiometricUseCase.VerifyCommand(request.challengeId(), request.installationId(),
                request.signature(), applicationCode);
        return ResponseEntity.ok(CommonResponseObject.success(BiometricErrors.translated(() -> biometric.verify(command))));
    }
}
