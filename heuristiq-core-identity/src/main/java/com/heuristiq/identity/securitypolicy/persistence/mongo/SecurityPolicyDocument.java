package com.heuristiq.identity.securitypolicy.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.identity.helper.AccountPolicy;
import com.heuristiq.identity.helper.ApiSecurityPolicy;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.LoginPolicy;
import com.heuristiq.identity.helper.MfaPolicy;
import com.heuristiq.identity.helper.PasswordPolicy;
import com.heuristiq.identity.helper.SessionPolicy;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "security_policies")
@CompoundIndexes({
    @CompoundIndex(name = "uk_tenant_application", def = "{'tenantId':1,'applicationId':1}", unique = true),
    @CompoundIndex(name = "idx_active", def = "{'active':1}")
})
public class SecurityPolicyDocument {

    @Id
    private String id;

    @org.springframework.data.annotation.Version
    private Long version;

    @Indexed
    private String tenantId;

    @Indexed
    private String applicationId;

    @NotBlank
    private String name;

    private String description;

    private PasswordPolicy passwordPolicy;

    private LoginPolicy loginPolicy;

    private SessionPolicy sessionPolicy;

    private MfaPolicy mfaPolicy;

    private ApiSecurityPolicy apiSecurityPolicy;

    private AccountPolicy accountPolicy;

    // Indexed via the class-level @CompoundIndex(name = "idx_active", def = "{'active':1}") above —
    // a field-level @Indexed here would create a second, conflicting index on the same key.
    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemPolicy = false;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveUntil;

    private AuditInfo audit;
}
