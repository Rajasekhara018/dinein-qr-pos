package com.heuristiq.uam.securitypolicy.persistence.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.helper.AccountPolicy;
import com.heuristiq.uam.helper.ApiSecurityPolicy;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.LoginPolicy;
import com.heuristiq.uam.helper.MfaPolicy;
import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.helper.SessionPolicy;
import com.heuristiq.uam.util.AccountPolicyAttributeConverter;
import com.heuristiq.uam.util.ApiSecurityPolicyAttributeConverter;
import com.heuristiq.uam.util.LoginPolicyAttributeConverter;
import com.heuristiq.uam.util.MfaPolicyAttributeConverter;
import com.heuristiq.uam.util.PasswordPolicyAttributeConverter;
import com.heuristiq.uam.util.SessionPolicyAttributeConverter;

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
