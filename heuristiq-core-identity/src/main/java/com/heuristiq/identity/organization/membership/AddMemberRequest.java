package com.heuristiq.uam.organization.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Makes an existing account a member of the organization.")
public record AddMemberRequest(
        @Schema(description = "Id of the account.") @NotBlank @Size(max = 255, message = "must be at most 255 characters") String userId
) {
}
