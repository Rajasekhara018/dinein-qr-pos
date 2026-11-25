package com.heuristiq.identity.usercredentials.persistance.jpa;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import com.heuristiq.identity.util.JsonMapAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "user_credentials", uniqueConstraints = @UniqueConstraint(name = "uk_provider_provider_user_id", columnNames = {"provider", "provider_user_id"}))
public class UserCredentialEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank
    private String userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    @Size(max = 255)
    @Column(name = "provider_user_id")
    private String providerUserId;

    @JsonIgnore
    private String passwordHash;

    @JsonIgnore
    private String passwordSalt;

    @JsonIgnore
    private String temporaryPasswordHash;

    private LocalDateTime temporaryPasswordExpiry;

    private LocalDateTime passwordChangedAt;

    private LocalDateTime passwordExpiryAt;

    private Boolean forcePasswordChange;

    private Boolean primaryCredential;

    private Boolean verified;

    private Boolean emailVerified;

    private Boolean phoneVerified;

    @JsonIgnore
    private String refreshToken;

    @JsonIgnore
    private String accessToken;

    private LocalDateTime tokenExpiry;

    private LocalDateTime lastAuthenticatedAt;

    private LocalDateTime lastSyncAt;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    private Boolean active;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Version
    private Long version;
}
