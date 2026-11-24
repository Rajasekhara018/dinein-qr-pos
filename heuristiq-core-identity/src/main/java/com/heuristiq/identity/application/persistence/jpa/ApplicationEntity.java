package com.heuristiq.uam.application.persistence.jpa;

import java.util.Map;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.enums.UamEnums.ApplicationType;
import com.heuristiq.uam.enums.UamEnums.AuthenticationMode;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RUpload;
import com.heuristiq.uam.util.JsonMapAttributeConverter;

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
import jakarta.validation.constraints.Email;
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
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(name = "uk_application_code", columnNames = "code"))
public class ApplicationEntity {

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

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    private String appVersion;

    private String baseUrl;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "icon_name")),
        @AttributeOverride(name = "fileType", column = @Column(name = "icon_file_type")),
        @AttributeOverride(name = "size", column = @Column(name = "icon_size")),
        @AttributeOverride(name = "location", column = @Column(name = "icon_location")),
        @AttributeOverride(name = "title", column = @Column(name = "icon_title")),
        @AttributeOverride(name = "prefixFolder", column = @Column(name = "icon_prefix_folder")),
        @AttributeOverride(name = "modelKey", column = @Column(name = "icon_model_key")),
        @AttributeOverride(name = "retentionPolicy", column = @Column(name = "icon_retention_policy")),
        @AttributeOverride(name = "versionId", column = @Column(name = "icon_version_id"))
    })
    private RUpload icon;

    private String landingPage;

    private String ownerTeam;

    @Email
    private String supportEmail;

    @Enumerated(EnumType.STRING)
    private ApplicationType type;

    @Enumerated(EnumType.STRING)
    private AuthenticationMode authenticationMode;

    @Builder.Default
    private Boolean ssoEnabled = true;

    @Builder.Default
    private Boolean mfaRequired = false;

    private Integer displayOrder;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemApplication = false;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> attributes;

    @Embedded
    private AuditInfo audit;
}
