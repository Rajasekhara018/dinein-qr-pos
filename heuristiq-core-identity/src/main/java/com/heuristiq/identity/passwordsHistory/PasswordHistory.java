package com.heuristiq.identity.passwordsHistory;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.PasswordChangeReason;
import com.heuristiq.identity.helper.AuditInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code persistence.mongo.PasswordHistoryDocument} /
 * {@code persistence.jpa.PasswordHistoryEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordHistory {

    private String id;

    private String userId;

    private String credentialId;

    @JsonIgnore
    private String passwordHash;

    private String algorithm;

    private Integer passwordVersion;

    private PasswordChangeReason changeReason;

    private String changedBy;

    private LocalDateTime passwordExpiryAt;

    private AuditInfo audit;
}
