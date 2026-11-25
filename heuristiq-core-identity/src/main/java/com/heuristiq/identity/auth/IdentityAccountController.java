package com.heuristiq.identity.auth;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.dto.IdentityChangePasswordRequest;
import com.heuristiq.identity.usercredentials.OwnPasswordChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service for any signed-in account, whatever its role (a customer needs no user-management privilege). It lives under
 * {@code /identity/account}, which is authenticated-only and not in the privilege catalog; the account is always the token's subject.
 */
@RestController
@RequestMapping("/api/v1/uam/identity/account")
@Tag(name = "Identity", description = "Self-service calls of the signed-in account.")
public class IdentityAccountController {
    private final OwnPasswordChangeService passwordChange;
    private final CommonRequestMapper requestMapper;

    public IdentityAccountController(OwnPasswordChangeService passwordChange, CommonRequestMapper requestMapper) {
        this.passwordChange = passwordChange;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Change my password",
            description = "Changes the signed-in account's own password. The current password (for a first sign-in, the temporary one) must be correct and the new one different. Clears the must-change flag. Request payload is an IdentityChangePasswordRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Validation failed, current password incorrect, or the new password equals it"),
            @ApiResponse(responseCode = "401", description = "Not signed in"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(IdentityChangePasswordRequest.class)
    @PostMapping("/change-password")
    public ResponseEntity<CommonResponseObject> changePassword(Authentication authentication,
                                                               @Valid @RequestBody CommonRequestObject commonRequest) {
        IdentityChangePasswordRequest request = requestMapper.requireRequestObject(commonRequest, IdentityChangePasswordRequest.class);
        passwordChange.change(authentication.getName(), request.oldPassword(), request.newPassword());
        return ResponseEntity.ok(CommonResponseObject.success("Password changed", null));
    }
}
