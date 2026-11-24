package com.heuristiq.uam.application.persistence.mongo;

import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.uam.enums.UamEnums.ApplicationType;
import com.heuristiq.uam.enums.UamEnums.AuthenticationMode;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RUpload;
import com.heuristiq.uam.util.ImmutableAfterCreate;

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
@Document(collection = "applications")
@CompoundIndexes({
    @CompoundIndex(name = "uk_application_code", def = "{'code':1}", unique = true),
    @CompoundIndex(name = "idx_active", def = "{'active':1}")
})
public class ApplicationDocument {

    @Id
    private String id;

    // Indexed via the class-level @CompoundIndex(name = "uk_application_code", def = "{'code':1}", unique = true)
    // above — a field-level @Indexed here would create a second, conflicting index on the same key.
    @ImmutableAfterCreate
    @NotBlank
    @Size(max = 100)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    private String appVersion;

    private String baseUrl;

    private RUpload icon;

    private String landingPage;

    @Indexed
    private String ownerTeam;

    @Email
    private String supportEmail;

    @Indexed
    private ApplicationType type;

    @Indexed
    private AuthenticationMode authenticationMode;

    @Builder.Default
    private Boolean ssoEnabled = true;

    @Builder.Default
    private Boolean mfaRequired = false;

    private Integer displayOrder;

    // Indexed via the class-level @CompoundIndex(name = "idx_active", def = "{'active':1}") above —
    // a field-level @Indexed here would create a second, conflicting index on the same key.
    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemApplication = false;

    private Map<String, Object> attributes;

    private AuditInfo audit;
}
