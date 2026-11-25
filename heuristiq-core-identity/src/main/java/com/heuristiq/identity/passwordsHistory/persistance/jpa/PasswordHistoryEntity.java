package com.heuristiq.identity.passwordsHistory.persistance.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.PasswordChangeReason;
import com.heuristiq.identity.helper.AuditInfo;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "password_history")
public class PasswordHistoryEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank(message = "User Id is required")
    private String userId;

    private String credentialId;

    @NotBlank
    @JsonIgnore
    private String passwordHash;

    private String algorithm;

    private Integer passwordVersion;

    @Enumerated(EnumType.STRING)
    private PasswordChangeReason changeReason;

    private String changedBy;

    private LocalDateTime passwordExpiryAt;

    @Embedded
    private AuditInfo audit;
}
