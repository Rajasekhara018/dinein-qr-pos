package com.heuristiq.identity.organization.persistence.mongo;

import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.identity.enums.UamEnums.LanguageEnum;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.RUpload;
import com.heuristiq.identity.util.ImmutableAfterCreate;

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
@Document(collection = "tenants")
@CompoundIndexes({

    @CompoundIndex(
            name = "uk_tenant_code",
            def = "{'code':1}",
            unique = true
    ),

    @CompoundIndex(
            name = "idx_active",
            def = "{'active':1}"
    )

})
public class TenantDocument {

    @Id
    private String id;

    // Indexed via the class-level @CompoundIndex(name = "uk_tenant_code", def = "{'code':1}", unique = true)
    // above — a field-level @Indexed here would create a second, conflicting index on the same key.
    @ImmutableAfterCreate
    @NotBlank
    @Size(max = 100)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    private String legalName;

    private String description;

    @Indexed
    private TenantType type;

    private RUpload logo;

    private String website;

    @Indexed
    private String emailDomain;

    private String timeZone;

    private LanguageEnum defaultLanguage;

    private String country;

    private String currency;

    private String supportEmail;

    private String supportPhone;

    @Indexed
    private TenantStatus status;

    private Integer maxUsers;

    private Integer maxApplications;

    // Indexed via the class-level @CompoundIndex(name = "idx_active", def = "{'active':1}") above —
    // a field-level @Indexed here would create a second, conflicting index on the same key.
    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemTenant = false;

    private Map<String,Object> settings;

    private AuditInfo audit;
}
