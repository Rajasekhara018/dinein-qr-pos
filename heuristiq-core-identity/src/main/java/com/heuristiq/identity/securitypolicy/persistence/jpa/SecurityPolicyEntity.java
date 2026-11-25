package com.heuristiq.identity.securitypolicy.persistence.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.identity.helper.AccountPolicy;
import com.heuristiq.identity.helper.ApiSecurityPolicy;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.LoginPolicy;
import com.heuristiq.identity.helper.MfaPolicy;
import com.heuristiq.identity.helper.PasswordPolicy;
import com.heuristiq.identity.helper.SessionPolicy;
import com.heuristiq.identity.util.AccountPolicyAttributeConverter;
import com.heuristiq.identity.util.ApiSecurityPolicyAttributeConverter;
import com.heuristiq.identity.util.LoginPolicyAttributeConverter;
import com.heuristiq.identity.util.MfaPolicyAttributeConverter;
import com.heuristiq.identity.util.PasswordPolicyAttributeConverter;
import com.heuristiq.identity.util.SessionPolicyAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "security_policies", uniqueConstraints = @UniqueConstraint(name = "uk_tenant_application", columnNames = {"tenant_id", "application_id"}))
public class SecurityPolicyEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @jakarta.persistence.Version
    @Column(name = "policy_revision")
    private Long version;

    private String tenantId;

    private String applicationId;

    @NotBlank
    private String name;

    private String description;

    @Convert(converter = PasswordPolicyAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private PasswordPolicy passwordPolicy;

    @Convert(converter = LoginPolicyAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private LoginPolicy loginPolicy;

    @Convert(converter = SessionPolicyAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private SessionPolicy sessionPolicy;

    @Convert(converter = MfaPolicyAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private MfaPolicy mfaPolicy;

    @Convert(converter = ApiSecurityPolicyAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private ApiSecurityPolicy apiSecurityPolicy;

    @Convert(converter = AccountPolicyAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private AccountPolicy accountPolicy;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemPolicy = false;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveUntil;

    @Embedded
    private AuditInfo audit;
}
