package com.heuristiq.identity.lockout;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam/users/{userId}/login-security")
@Tag(name = "Login Security", description = "Administrative view and control of a user's account-lockout state (failed attempts, locked flag, unlock).")
public class UserLoginSecurityController {
    private final UserLoginSecurityServiceUseCase loginSecurityService;
    private final UamUserRepository userRepository;
    private final LoginAttemptServiceUseCase loginAttemptService;

    public UserLoginSecurityController(UserLoginSecurityServiceUseCase loginSecurityService,
                                        UamUserRepository userRepository,
                                        LoginAttemptServiceUseCase loginAttemptService) {
        this.loginSecurityService = loginSecurityService;
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @Operation(summary = "Get a user's login-security status", description = "Returns the failed-login-attempt count and lockout flag for the given user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login-security status returned"),
            @ApiResponse(responseCode = "400", description = "No login-security record for this user"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public CommonResponseObject get(@Parameter(description = "User id") @PathVariable String userId) {
        return CommonResponseObject.success(
                loginSecurityService.getByUser(new UserLoginSecurityServiceUseCase.GetByUserInput(userId)).security());
    }

    @Operation(summary = "Unlock a user's account",
            description = "Clears both the failed-login-attempt lockout record and the underlying lockout gate, fully restoring the user's ability to log in. This is intentionally restricted to administrators only -- see UserLoginSecurityProfileController's javadoc for why self-unlock is not exposed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account unlocked"),
            @ApiResponse(responseCode = "400", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/unlock")
    public CommonResponseObject unlock(@Parameter(description = "User id") @PathVariable String userId) {
        // Clears both the per-user mirror record (UserLoginSecurity) and the actual lockout
        // gate (LoginAttemptService, keyed by username) — clearing only the former left the
        // user still blocked at login.
        UamUser user = userRepository.findById(userId)
                .orElseThrow(() -> UamException.notFound("User not found"));
        loginAttemptService.unlock(new LoginAttemptServiceUseCase.UnlockInput(user.getUserName()));
        return CommonResponseObject.success(
                loginSecurityService.unlock(new UserLoginSecurityServiceUseCase.UnlockInput(userId)).security());
    }
}
