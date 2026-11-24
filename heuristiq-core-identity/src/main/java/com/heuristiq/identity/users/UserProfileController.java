package com.heuristiq.uam.users;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.ChangePasswordRequest;
import com.heuristiq.uam.dto.UpdateMyProfileRequest;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile access for the caller's OWN account — deliberately separate from
 * {@link UserController} (which is SUPER_ADMIN-only and requires a client-supplied {@code {id}}).
 * The user id is always resolved server-side from the JWT subject (see JwtService/JwtAuthenticationFilter),
 * never from client input, so no explicit needed beyond SecurityConfig's
 * default `anyRequest().authenticated()`.
 */
@RestController
@RequestMapping({"/api/v1/uam/users/me", "/api/v1/uam/identity/me/profile"})
@Tag(name = "User Management", description = "Self-service profile and password management for the authenticated caller's own account.")
public class UserProfileController {
    private final UamUserServiceUseCase userService;
    private final UserCredentialServiceUseCase credentialService;
    private final CommonRequestMapper requestMapper;

    public UserProfileController(UamUserServiceUseCase userService, UserCredentialServiceUseCase credentialService,
                                  CommonRequestMapper requestMapper) {
        this.userService = userService;
        this.credentialService = credentialService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Get my profile", description = "Fetches the caller's own account details. User id is resolved server-side from the JWT, never client-supplied. Requires authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.get(new UamUserServiceUseCase.GetInput(authentication.getName())).user()));
    }

    @Operation(summary = "Update my profile", description = "Partially updates the caller's own profile fields (full name, email, phone, timezone, language, gender, profile image). Excludes username/roles/status/tenantId/userType, which only an administrator may change via UserController. Request payload is an UpdateMyProfileRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or email/phone already in use"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpdateMyProfileRequest.class)
    @PutMapping
    public ResponseEntity<CommonResponseObject> updateMyProfile(Authentication authentication,
                                                 @Valid @RequestBody CommonRequestObject commonRequest) {
        UpdateMyProfileRequest request = requestMapper.requireRequestObject(commonRequest, UpdateMyProfileRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.updateOwnProfile(new UamUserServiceUseCase.UpdateOwnProfileInput(
                        authentication.getName(), request)).user()));
    }

    @Operation(summary = "Change my password", description = "Changes the caller's own password. Requires the current password to be supplied and verified correct before the new one is set. Request payload is a ChangePasswordRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or current password incorrect"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(ChangePasswordRequest.class)
    @PatchMapping("/password")
    public ResponseEntity<CommonResponseObject> changeMyPassword(Authentication authentication,
                                                  @Valid @RequestBody CommonRequestObject commonRequest) {
        ChangePasswordRequest request = requestMapper.requireRequestObject(commonRequest, ChangePasswordRequest.class);
        String userId = authentication.getName();
        if (!credentialService.verifyPassword(new UserCredentialServiceUseCase.VerifyPasswordInput(
                userId, request.oldPassword())).verified()) {
            throw new UamException("Current password is incorrect");
        }
        credentialService.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(userId, request.newPassword(), false));
        return ResponseEntity.ok(CommonResponseObject.success("Password changed", null));
    }
}
