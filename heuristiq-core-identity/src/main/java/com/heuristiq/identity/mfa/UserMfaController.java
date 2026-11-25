package com.heuristiq.identity.mfa;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam")
@Tag(name = "User MFA", description = "Administrative management of users' multi-factor-authentication methods.")
public class UserMfaController {
    private final UserMfaServiceUseCase mfaService;

    public UserMfaController(UserMfaServiceUseCase mfaService) {
        this.mfaService = mfaService;
    }

    @Operation(summary = "List a user's MFA methods", description = "Returns every MFA method enrolled for the given user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA methods returned"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/users/{userId}/mfa")
    public CommonResponseObject listByUser(@Parameter(description = "User id") @PathVariable String userId) {
        return CommonResponseObject.success(
                mfaService.listByUser(new UserMfaServiceUseCase.ListByUserInput(userId)).mfas());
    }

    @Operation(summary = "Get an MFA method by id", description = "Fetches a single enrolled MFA method's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA method found"),
            @ApiResponse(responseCode = "404", description = "MFA method not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/mfa/{id}")
    public CommonResponseObject get(@Parameter(description = "MFA method id") @PathVariable String id) {
        return CommonResponseObject.success(
                mfaService.get(new UserMfaServiceUseCase.GetInput(id)).mfa());
    }

    @Operation(summary = "Remove an MFA method", description = "Permanently removes an enrolled MFA method.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA method removed"),
            @ApiResponse(responseCode = "404", description = "MFA method not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/mfa/{id}")
    public CommonResponseObject delete(@Parameter(description = "MFA method id") @PathVariable String id) {
        mfaService.delete(new UserMfaServiceUseCase.DeleteInput(id));
        return CommonResponseObject.success(OperationResult.completed("DELETE", "USER_MFA", id));
    }

    @Operation(summary = "Disable an MFA method", description = "Disables an enrolled MFA method without deleting its record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA method disabled"),
            @ApiResponse(responseCode = "404", description = "MFA method not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/mfa/{id}/disable")
    public CommonResponseObject disable(@Parameter(description = "MFA method id") @PathVariable String id) {
        return CommonResponseObject.success(
                mfaService.disable(new UserMfaServiceUseCase.DisableInput(id)).mfa());
    }
}
