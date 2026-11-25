package com.heuristiq.identity.auth;

import com.heuristiq.identity.common.CommonResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a signed-in client ask what it may do, so it can show or hide screens. Lives under
 * {@code /identity} (not {@code /users/me}) so it is not caught by the user-management privilege entries:
 * any authenticated caller may read their own privileges, and only their own.
 */
@RestController
@RequestMapping("/api/v1/uam/identity/me")
@Tag(name = "Identity", description = "Self-service identity information for the authenticated caller.")
public class MyPrivilegesController {

    private final MyPrivilegesServiceUseCase myPrivilegesService;

    public MyPrivilegesController(MyPrivilegesServiceUseCase myPrivilegesService) {
        this.myPrivilegesService = myPrivilegesService;
    }

    @Operation(summary = "My privileges",
            description = "Returns the caller's roles, the permission codes and the gateway privilege ids those roles grant. "
                    + "A UI hint only; the gateway still authorizes every request. Requires authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Privileges returned"),
            @ApiResponse(responseCode = "400", description = "The caller's account no longer exists"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/privileges")
    public CommonResponseObject myPrivileges(Authentication authentication) {
        // Only the roles the caller's token carries count, as at the gateway (a franchise role without an active franchise is not among them).
        java.util.List<String> sessionAuthorities = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .toList();
        return CommonResponseObject.success(myPrivilegesService.forUser(
                new MyPrivilegesServiceUseCase.ForUserInput(authentication.getName(), sessionAuthorities)).privileges());
    }
}
