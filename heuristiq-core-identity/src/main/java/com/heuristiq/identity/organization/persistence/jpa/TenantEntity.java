package com.heuristiq.identity.organization.persistence.jpa;

import java.util.Map;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.identity.enums.UamEnums.LanguageEnum;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.RUpload;
import com.heuristiq.identity.util.JsonMapAttributeConverter;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenants", uniqueConstraints = @UniqueConstraint(name = "uk_tenant_code", columnNames = "code"))
public class TenantEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank
    @Size(max = 100)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    private String legalName;

    private String description;

    @Enumerated(EnumType.STRING)
    private TenantType type;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "logo_name")),
        @AttributeOverride(name = "fileType", column = @Column(name = "logo_file_type")),
        @AttributeOverride(name = "size", column = @Column(name = "logo_size")),
        @AttributeOverride(name = "location", column = @Column(name = "logo_location")),
        @AttributeOverride(name = "title", column = @Column(name = "logo_title")),
        @AttributeOverride(name = "prefixFolder", column = @Column(name = "logo_prefix_folder")),
        @AttributeOverride(name = "modelKey", column = @Column(name = "logo_model_key")),
        @AttributeOverride(name = "retentionPolicy", column = @Column(name = "logo_retention_policy")),
        @AttributeOverride(name = "versionId", column = @Column(name = "logo_version_id"))
    })
    private RUpload logo;

    private String website;

    private String emailDomain;

    private String timeZone;

    @Enumerated(EnumType.STRING)
    private LanguageEnum defaultLanguage;

    private String country;

    private String currency;

    private String supportEmail;

    private String supportPhone;

    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    private Integer maxUsers;

    private Integer maxApplications;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemTenant = false;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String,Object> settings;

    @Embedded
    private AuditInfo audit;
}
