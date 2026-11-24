package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.helper.AccountPolicy;
import com.heuristiq.uam.helper.ApiSecurityPolicy;
import com.heuristiq.uam.helper.LoginPolicy;
import com.heuristiq.uam.helper.MfaPolicy;
import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.helper.SessionPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Create/update payload for a security policy, optionally scoped to a tenant and/or application.")
public record UpsertSecurityPolicyRequest(
        @Schema(description = "Tenant this policy applies to. Null means it applies globally.") String tenantId,
        @Schema(description = "Application this policy applies to. Null means it applies to all applications for the tenant.") String applicationId,
        @Schema(description = "Policy name.", example = "Default Security Policy") @NotBlank String name,
        @Schema(description = "Free-text description.") String description,
        @Schema(description = "Password strength/rotation rules.") PasswordPolicy passwordPolicy,
        @Schema(description = "Login/lockout rules.") LoginPolicy loginPolicy,
        @Schema(description = "Session timeout/concurrency rules.") SessionPolicy sessionPolicy,
        @Schema(description = "MFA enforcement rules.") MfaPolicy mfaPolicy,
        @Schema(description = "API/client security rules.") ApiSecurityPolicy apiSecurityPolicy,
        @Schema(description = "Account lockout/activation rules.") AccountPolicy accountPolicy,
        Boolean active,
        java.time.LocalDateTime effectiveFrom,
        java.time.LocalDateTime effectiveUntil,
        Long version
) {
    public UpsertSecurityPolicyRequest(String tenantId, String applicationId, String name, String description,
            PasswordPolicy passwordPolicy, LoginPolicy loginPolicy, SessionPolicy sessionPolicy, MfaPolicy mfaPolicy,
            ApiSecurityPolicy apiSecurityPolicy, AccountPolicy accountPolicy) {
        this(tenantId, applicationId, name, description, passwordPolicy, loginPolicy, sessionPolicy, mfaPolicy,
                apiSecurityPolicy, accountPolicy, true, null, null, null);
    }
}
