package com.heuristiq.identity.dto;

import com.heuristiq.identity.usercredentials.UserCredential;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Read-only summary of one authentication credential linked to a user (local password, Google, external IdP, etc), with secrets omitted.")
public record CredentialSummary(
        @Schema(description = "Credential id.") String id,
        @Schema(description = "The authentication provider/mechanism this credential belongs to.") AuthProvider provider,
        @Schema(description = "The provider's own identifier for this credential, if applicable (e.g. Google subject id).") String providerUserId,
        @Schema(description = "Whether this credential has been verified (e.g. email/phone OTP confirmed).") Boolean verified,
        @Schema(description = "Whether this is the user's primary sign-in credential.") Boolean primaryCredential,
        @Schema(description = "Whether this credential is currently active/usable.") Boolean active,
        @Schema(description = "Timestamp of the last successful authentication with this credential.") LocalDateTime lastAuthenticatedAt
) {
    public static CredentialSummary from(UserCredential credential) {
        return new CredentialSummary(
                credential.getId(),
                credential.getProvider(),
                credential.getProviderUserId(),
                credential.getVerified(),
                credential.getPrimaryCredential(),
                credential.getActive(),
                credential.getLastAuthenticatedAt());
    }
}
