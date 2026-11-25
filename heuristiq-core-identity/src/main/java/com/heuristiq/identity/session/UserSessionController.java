package com.heuristiq.identity.session;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.OperationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam")
@Tag(name = "User Sessions", description = "Administrative management of users' active login sessions.")
public class UserSessionController {
    private final UserSessionServiceUseCase sessionService;

    public UserSessionController(UserSessionServiceUseCase sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "List a user's sessions", description = "Returns every active session for the given user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessions returned"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/users/{userId}/sessions")
    public CommonResponseObject listByUser(@Parameter(description = "User id") @PathVariable String userId) {
        return CommonResponseObject.success(
                sessionService.listByUser(new UserSessionServiceUseCase.ListByUserInput(userId)).sessions());
    }

    @Operation(summary = "Revoke a session", description = "Immediately revokes a single session, ending it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session revoked"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/sessions/{sessionId}")
    public CommonResponseObject revoke(@Parameter(description = "Session id") @PathVariable String sessionId) {
        sessionService.revoke(new UserSessionServiceUseCase.RevokeInput(sessionId));
        return CommonResponseObject.success(OperationResult.completed("REVOKE", "USER_SESSION", sessionId));
    }

    @Operation(summary = "Revoke all sessions for a user", description = "Immediately revokes every active session for the given user, e.g. to force a full logout everywhere.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All sessions for the user revoked"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/users/{userId}/sessions")
    public CommonResponseObject revokeAll(@Parameter(description = "User id") @PathVariable String userId) {
        sessionService.revokeAllForUser(new UserSessionServiceUseCase.RevokeAllForUserInput(userId));
        return CommonResponseObject.success(OperationResult.completed("REVOKE_ALL", "USER_SESSION", userId));
    }
}
