package com.heuristiq.uam.passwordsHistory;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.passwordsHistory.PasswordHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam/users/{userId}/password-history")
@Tag(name = "Password History", description = "Administrative, read-only access to a user's past password change history (used to enforce password-reuse policies).")
public class PasswordHistoryController {
    private final PasswordHistoryServiceUseCase passwordHistoryService;

    public PasswordHistoryController(PasswordHistoryServiceUseCase passwordHistoryService) {
        this.passwordHistoryService = passwordHistoryService;
    }

    @Operation(summary = "List a user's password history", description = "Returns a page of a user's past password-change records (hashes/timestamps only, never plaintext).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of password history entries returned"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public CommonResponseObject list(@Parameter(description = "User id") @PathVariable String userId,
                               @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
        return CommonResponseObject.success(
                passwordHistoryService.listByUser(new PasswordHistoryServiceUseCase.ListByUserInput(userId, page, size)).entries());
    }
}
