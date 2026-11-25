package com.heuristiq.identity.refreshToken.persistance.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.PrincipalType;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.util.ImmutableAfterCreate;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "refresh_tokens")
@CompoundIndexes({
    @CompoundIndex(name = "uk_token", def = "{'tokenId':1}", unique = true),
    @CompoundIndex(name = "idx_principal", def = "{'principalId':1,'active':1}"),
    @CompoundIndex(name = "idx_expiry", def = "{'expiresAt':1}"),
    @CompoundIndex(name = "idx_client", def = "{'clientId':1}")
})
public class RefreshTokenDocument {

    @Id
    private String id;

    @Indexed
    @NotBlank
    private String principalId;

    @Indexed
    private PrincipalType principalType;

    // Indexed via the class-level @CompoundIndex(name = "idx_client", def = "{'clientId':1}") above —
    // a field-level @Indexed here would create a second, conflicting index on the same key.
    private String clientId;

    // Indexed via the class-level @CompoundIndex(name = "uk_token", def = "{'tokenId':1}", unique = true)
    // above — a field-level @Indexed here would create a second, conflicting index on the same key.
    @ImmutableAfterCreate
    private String tokenId;

    @JsonIgnore
    @Indexed
    private String tokenHash;

    @Indexed
    private String jwtId;

    @Indexed
    private String sessionId;

    private String deviceId;

    private LocalDateTime issuedAt;

    // Indexed via the class-level @CompoundIndex(name = "idx_expiry", def = "{'expiresAt':1}") above —
    // a field-level @Indexed here would create a second, conflicting index on the same key.
    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    @Builder.Default
    @Indexed
    private Boolean revoked = false;

    private String revokedReason;

    private LocalDateTime revokedAt;

    // Reuse detection revokes every active token of a family; indexed so that lookup is not a collection scan.
    @Indexed
    private String familyId;

    private String successorTokenId;

    private Integer graceRedemptions;

    @Builder.Default
    private Integer rotationCount = 0;

    private String ipAddress;

    private String userAgent;

    private AuditInfo audit;
}
