package com.heuristiq.identity.mfa.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.MfaType;
import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.util.JsonMapAttributeConverter;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_mfa", uniqueConstraints = @UniqueConstraint(name = "uk_user_provider", columnNames = {"user_id", "mfa_type"}))
public class UserMfaEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank(message = "User Id is required")
    private String userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    private MfaType mfaType;

    @JsonIgnore
    private String secret;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String qrCodeUrl;

    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_mfa_backup_codes", joinColumns = @JoinColumn(name = "user_mfa_id"))
    @Column(name = "backup_code")
    @OrderColumn(name = "idx")
    private List<String> backupCodes;

    @Embedded
    private RPhoneNumber phoneNumber;

    @Email
    private String email;

    private Boolean enabled = false;

    @Column(name = "is_primary")
    private Boolean primary = false;

    private Boolean verified = false;

    private LocalDateTime lastVerifiedAt;

    private Integer failedAttempts = 0;

    private Boolean locked = false;

    private LocalDateTime lockedUntil;

    private String deviceId;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    private Boolean active = true;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Version
    private Long version;
}
