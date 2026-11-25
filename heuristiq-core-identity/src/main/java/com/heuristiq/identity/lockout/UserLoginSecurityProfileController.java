package com.heuristiq.identity.lockout;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.UamException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service login-security status — read-only by design. Unlocking fully clears the lockout
 * state (including failedLoginAttempts) with no re-verification step, so exposing it to the
 * locked-out user themselves would let a lockout be undone by whoever caused it (e.g. an attacker
 * still guessing the password). Unlock stays SUPER_ADMIN-only on {@link UserLoginSecurityController}.
 */
@RestController
@RequestMapping({"/api/v1/uam/users/me/login-security", "/api/v1/uam/identity/me/login-security"})
@Tag(name = "Login Security", description = "Self-service, read-only view of the authenticated caller's own account-lockout status.")
public class UserLoginSecurityProfileController {
    private final UserLoginSecurityServiceUseCase loginSecurityService;

    public UserLoginSecurityProfileController(UserLoginSecurityServiceUseCase loginSecurityService) {
        this.loginSecurityService = loginSecurityService;
    }

    @Operation(summary = "Get my login-security status",
            description = "Returns the caller's own failed-login-attempt count and lockout flag. Read-only -- self-unlock is deliberately not exposed here (see class javadoc); use an administrator's unlock endpoint instead. Returns safe defaults (unlocked, zero attempts) if no record exists yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login-security status returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public CommonResponseObject getMine(Authentication authentication) {
        try {
            return CommonResponseObject.success(loginSecurityService.getByUser(
                    new UserLoginSecurityServiceUseCase.GetByUserInput(authentication.getName())).security());
        } catch (UamException e) {
            // No login-security record yet (e.g. brand-new account) -- return safe defaults
            // instead of a 400-shaped failure.
            return CommonResponseObject.success(UserLoginSecurity.builder()
                    .userId(authentication.getName())
                    .failedLoginAttempts(0)
                    .accountLocked(false)
                    .build());
        }
    }
}
