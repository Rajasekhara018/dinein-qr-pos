package com.heuristiq.uam.session;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.common.UamException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service session access — see {@link com.heuristiq.uam.users.UserProfileController}'s javadoc for the pattern.
 * {@code UserSessionService#revoke} takes a raw session id with no ownership check, so
 * the single-session revoke below verifies {@code session.getUserId()} against the caller before
 * acting, to stop one user from revoking another user's session by guessing/enumerating ids.
 */
@RestController
@RequestMapping({"/api/v1/uam/users/me/sessions", "/api/v1/uam/identity/me/sessions"})
@Tag(name = "User Sessions", description = "Self-service management of the authenticated caller's own active login sessions.")
public class UserSessionProfileController {
    private final UserSessionServiceUseCase sessionService;

    public UserSessionProfileController(UserSessionServiceUseCase sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "List my sessions", description = "Returns every active session for the caller's own account. Requires authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public CommonResponseObject listMine(Authentication authentication) {
        return CommonResponseObject.success(
                sessionService.listByUser(new UserSessionServiceUseCase.ListByUserInput(authentication.getName())).sessions());
    }

    @Operation(summary = "Revoke one of my sessions", description = "Immediately revokes a single session belonging to the caller. Rejects the request if the session does not belong to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session revoked"),
            @ApiResponse(responseCode = "400", description = "Session not found or not owned by the caller"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{sessionId}")
    public CommonResponseObject revokeMine(Authentication authentication, @Parameter(description = "Session id") @PathVariable String sessionId) {
        UserSession session = sessionService.get(new UserSessionServiceUseCase.GetInput(sessionId)).session();
        if (!authentication.getName().equals(session.getUserId())) {
            throw new UamException("Session not found");
        }
        sessionService.revoke(new UserSessionServiceUseCase.RevokeInput(sessionId));
        return CommonResponseObject.success(OperationResult.completed("REVOKE", "USER_SESSION", sessionId));
    }

    @Operation(summary = "Revoke all my sessions", description = "Immediately revokes every active session for the caller's own account, e.g. to force a full logout everywhere.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All of the caller's sessions revoked"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping
    public CommonResponseObject revokeAllMine(Authentication authentication) {
        sessionService.revokeAllForUser(new UserSessionServiceUseCase.RevokeAllForUserInput(authentication.getName()));
        return CommonResponseObject.success(OperationResult.completed("REVOKE_ALL", "USER_SESSION", authentication.getName()));
    }
}
