package com.heuristiq.uam.mfa.persistence.mongo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.MfaType;
import com.heuristiq.uam.helper.RPhoneNumber;

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
@Document(collection = "user_mfa")
@CompoundIndexes({
    @CompoundIndex(name = "uk_user_provider", def = "{'userId':1,'mfaType':1}", unique = true),
    @CompoundIndex(name = "idx_user_enabled", def = "{'userId':1,'enabled':1}")
})
public class UserMfaDocument {

    @Id
    private String id;

    @NotBlank(message = "User Id is required")
    @Indexed
    private String userId;

    @NotNull
    @Indexed
    private MfaType mfaType;

    @JsonIgnore
    private String secret;

    @JsonIgnore
    private String qrCodeUrl;

    @JsonIgnore
    private List<String> backupCodes;

    private RPhoneNumber phoneNumber;

    @Email
    private String email;

    @Indexed
    private Boolean enabled = false;

    private Boolean primary = false;

    private Boolean verified = false;

    @Indexed
    private LocalDateTime lastVerifiedAt;

    private Integer failedAttempts = 0;

    @Indexed
    private Boolean locked = false;

    @Indexed
    private LocalDateTime lockedUntil;

    @Indexed
    private String deviceId;

    private Map<String, Object> metadata;

    @Indexed
    private Boolean active = true;

    @Indexed
    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Version
    private Long version;
}
