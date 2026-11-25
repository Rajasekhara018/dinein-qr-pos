package com.heuristiq.identity.passwordsHistory.persistance.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.PasswordChangeReason;
import com.heuristiq.identity.helper.AuditInfo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "password_history")
@CompoundIndexes({
    @CompoundIndex(name = "idx_user_createdAt", def = "{'userId':1,'createdAt':-1}"),
    @CompoundIndex(name = "idx_user_passwordHash", def = "{'userId':1,'passwordHash':1}")
})
public class PasswordHistoryDocument {

    @Id
    private String id;

    @NotBlank(message = "User Id is required")
    @Indexed
    private String userId;

    @Indexed
    private String credentialId;

    @NotBlank
    @JsonIgnore
    private String passwordHash;

    private String algorithm;

    private Integer passwordVersion;

    private PasswordChangeReason changeReason;

    @Indexed
    private String changedBy;

    @Indexed
    private LocalDateTime passwordExpiryAt;

    private AuditInfo audit;
}
