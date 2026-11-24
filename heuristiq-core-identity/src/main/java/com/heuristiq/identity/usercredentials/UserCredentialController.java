package com.heuristiq.uam.usercredentials;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.dto.CredentialSummary;
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
@Tag(name = "User Credentials", description = "Administrative management of users' linked authentication credentials (local password, Google, external IdP).")
public class UserCredentialController {
    private final UserCredentialServiceUseCase userCredentialService;

    public UserCredentialController(UserCredentialServiceUseCase userCredentialService) {
        this.userCredentialService = userCredentialService;
    }

    @Operation(summary = "List a user's credentials", description = "Returns a summary (secrets omitted) of every authentication credential linked to the given user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credential summaries returned"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/users/{userId}/credentials")
    public CommonResponseObject listByUser(@Parameter(description = "User id") @PathVariable String userId) {
        return CommonResponseObject.success(
                userCredentialService.listByUser(new UserCredentialServiceUseCase.ListByUserInput(userId)).credentials().stream()
                        .map(CredentialSummary::from)
                        .toList());
    }

    @Operation(summary = "Deactivate a credential", description = "Deactivates a linked authentication credential, preventing it from being used to log in, without deleting its history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credential deactivated"),
            @ApiResponse(responseCode = "404", description = "Credential not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/credentials/{id}")
    public CommonResponseObject deactivate(@Parameter(description = "Credential id") @PathVariable String id) {
        userCredentialService.deactivate(new UserCredentialServiceUseCase.DeactivateInput(id));
        return CommonResponseObject.success(OperationResult.completed("DEACTIVATE", "USER_CREDENTIAL", id));
    }
}
