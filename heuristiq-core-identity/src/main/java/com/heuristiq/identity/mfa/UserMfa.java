package com.heuristiq.uam.mfa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.MfaType;
import com.heuristiq.uam.helper.RPhoneNumber;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code mfa.persistence.mongo.UserMfaDocument} / {@code mfa.persistence.jpa.UserMfaEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMfa {

    private String id;

    private String userId;

    private MfaType mfaType;

    @JsonIgnore
    private String secret;

    @JsonIgnore
    private String qrCodeUrl;

    @JsonIgnore
    private List<String> backupCodes;

    private RPhoneNumber phoneNumber;

    private String email;

    private Boolean enabled = false;

    private Boolean primary = false;

    private Boolean verified = false;

    private LocalDateTime lastVerifiedAt;

    private Integer failedAttempts = 0;

    private Boolean locked = false;

    private LocalDateTime lockedUntil;

    private String deviceId;

    private Map<String, Object> metadata;

    private Boolean active = true;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    private Long version;
}
