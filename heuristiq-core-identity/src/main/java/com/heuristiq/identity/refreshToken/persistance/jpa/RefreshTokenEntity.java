package com.heuristiq.identity.refreshToken.persistance.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.PrincipalType;
import com.heuristiq.identity.helper.AuditInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "refresh_tokens", uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_id", columnNames = "token_id"))
public class RefreshTokenEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank
    private String principalId;

    @Enumerated(EnumType.STRING)
    private PrincipalType principalType;

    private String clientId;

    @Column(name = "token_id", unique = true)
    private String tokenId;

    @JsonIgnore
    private String tokenHash;

    private String jwtId;

    private String sessionId;

    private String deviceId;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    @Builder.Default
    private Boolean revoked = false;

    private String revokedReason;

    private LocalDateTime revokedAt;

    private String familyId;

    private String successorTokenId;

    private Integer graceRedemptions;

    @Builder.Default
    private Integer rotationCount = 0;

    private String ipAddress;

    private String userAgent;

    @Embedded
    private AuditInfo audit;
}
