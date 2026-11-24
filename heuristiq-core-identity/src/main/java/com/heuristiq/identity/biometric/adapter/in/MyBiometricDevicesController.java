package com.heuristiq.uam.biometric.adapter.in;

import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.biometric.application.BiometricUseCase;
import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service for the signed-in account's fingerprint / Face ID devices. Lives under {@code /identity/me}, which is
 * authenticated-only and not in the privilege catalog; the account is always the token's subject, so a person can only see
 * and revoke their own devices, whatever organization the session is in.
 */
@RestController
@RequestMapping(BiometricWebConstants.MY_DEVICES_PATH)
@Tag(name = "Biometric sign-in", description = "Sign in with fingerprint / Face ID: enrol, list and revoke the caller's own devices.")
public class MyBiometricDevicesController {

    private final BiometricUseCase biometric;
    private final CommonRequestMapper requestMapper;

    public MyBiometricDevicesController(BiometricUseCase biometric, CommonRequestMapper requestMapper) {
        this.biometric = biometric;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Enrol this device",
            description = "Stores this app installation's device public key so the account can sign in with fingerprint / Face ID. "
                    + "Needs a sign-in (password, one-time code, Google) within the last few minutes, or the correct currentPassword; "
                    + "otherwise 401 REAUTH_REQUIRED. Re-enrolling the same installation replaces its previous key. "
                    + "The X-Application-Code header names the application the device signs in to. Request payload is BiometricRequests.Enrol.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Enrolled: credentialId, createdAt, expiresAt"),
            @ApiResponse(responseCode = "400", description = "Unsupported key, algorithm or platform, bad installation id, or no X-Application-Code"),
            @ApiResponse(responseCode = "401", description = "Not signed in, or REAUTH_REQUIRED"),
            @ApiResponse(responseCode = "409", description = "A concurrent enrolment of the same installation won; retry")
    })
    @RequestObjectType(BiometricRequests.Enrol.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> enrol(Authentication authentication,
                                                      @RequestHeader(value = AuthConstants.APPLICATION_CODE_HEADER, required = false) String applicationCode,
                                                      @Valid @RequestBody CommonRequestObject commonRequest,
                                                      HttpServletRequest httpRequest) {
        BiometricRequests.Enrol request = requestMapper.requireRequestObject(commonRequest, BiometricRequests.Enrol.class);
        Object sessionId = httpRequest.getAttribute(BiometricWebConstants.SESSION_ID_ATTRIBUTE);
        BiometricUseCase.EnrolCommand command = new BiometricUseCase.EnrolCommand(authentication.getName(), TenantContext.get(),
                applicationCode, sessionId == null ? null : sessionId.toString(), request.installationId(), request.publicKey(),
                request.keyAlgorithm(), request.platform(), request.deviceName(), request.currentPassword());
        return ResponseEntity.ok(CommonResponseObject.success(
                BiometricResponses.Enrolled.of(BiometricErrors.translated(() -> biometric.enrol(command)))));
    }

    @Operation(summary = "List my devices",
            description = "The caller's active fingerprint / Face ID devices, newest first. 'current' marks the installation named in the X-Installation-Id header.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices returned"),
            @ApiResponse(responseCode = "401", description = "Not signed in")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> listMine(Authentication authentication,
                                                         @RequestHeader(value = BiometricWebConstants.INSTALLATION_ID_HEADER, required = false) String installationId) {
        return ResponseEntity.ok(CommonResponseObject.success(biometric.listMine(authentication.getName(), installationId).stream()
                .map(BiometricResponses.Device::of).toList()));
    }

    @Operation(summary = "Revoke one of my devices", description = "Turns fingerprint / Face ID sign-in off for one of the caller's devices.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revoked (also when it already was)"),
            @ApiResponse(responseCode = "401", description = "Not signed in"),
            @ApiResponse(responseCode = "404", description = "Not one of the caller's devices")
    })
    @DeleteMapping("/{credentialId}")
    public ResponseEntity<CommonResponseObject> revokeMine(Authentication authentication,
                                                           @Parameter(description = "credentialId from the list") @PathVariable String credentialId) {
        BiometricErrors.run(() -> biometric.revokeMine(authentication.getName(), credentialId));
        return ResponseEntity.ok(CommonResponseObject.success(OperationResult.completed(BiometricWebConstants.OPERATION_REVOKE,
                BiometricWebConstants.RESULT_ENTITY, credentialId)));
    }

    @Operation(summary = "Turn off on this phone", description = "Revokes the caller's active credentials of one app installation. Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revoked (also when there was nothing to revoke)"),
            @ApiResponse(responseCode = "400", description = "Malformed installation id"),
            @ApiResponse(responseCode = "401", description = "Not signed in")
    })
    @DeleteMapping("/installation/{installationId}")
    public ResponseEntity<CommonResponseObject> revokeMineByInstallation(Authentication authentication,
                                                                         @Parameter(description = "The app's installation id") @PathVariable String installationId) {
        BiometricErrors.run(() -> biometric.revokeMineByInstallation(authentication.getName(), installationId));
        return ResponseEntity.ok(CommonResponseObject.success(OperationResult.completed(BiometricWebConstants.OPERATION_REVOKE,
                BiometricWebConstants.RESULT_ENTITY, installationId)));
    }
}
