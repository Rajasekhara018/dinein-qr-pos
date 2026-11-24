package com.heuristiq.uam.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.heuristiq.uam.enums.UamEnums.Gender;
import com.heuristiq.uam.enums.UamEnums.LanguageEnum;
import com.heuristiq.uam.enums.UamEnums.UserStatus;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.helper.RPhoneNumber;
import com.heuristiq.uam.helper.RUpload;
import com.heuristiq.uam.search.SearchIndexable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure domain model -- see {@code persistence.mongo.UamUserDocument} / {@code persistence.jpa.UamUserEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UamUser implements SearchIndexable {

    private String id;

    /**
     * Tenant.
     * Null = Global Group
     */
    private String tenantId;

    /**
     * Immutable unique username used for login.
     */
    private String userName;

    /**
     * Primary email address.
     */
    private String email;

    /**
     * Primary phone number.
     */
    private RPhoneNumber phoneNumber;

    /**
     * Employee/Customer code.
     */
    private String employeeCode;

    /**
     * Display name shown across applications.
     */
    private String displayName;

    /**
     * Complete name.
     */
    private RName fullName;

    /**
     * Account status.
     */
    private UserStatus status;

    /**
     * Department.
     */
    private String departmentId;

    /**
     * Reporting manager.
     */
    private String reportingManagerId;

    /**
     * Designation.
     */
    private String designationId;

    /**
     * Location.
     */
    private String locationId;

    /**
     * Timezone.
     */
    private String timezone;

    /**
     * Preferred language.
     */
    private LanguageEnum preferredLanguage;

    /**
     * User profile image.
     */
    private RUpload profileImage;

    /**
     * Gender.
     */
    private Gender gender;

    /**
     * Joining date.
     */
    private LocalDate joiningDate;

    /**
     * Relieving date.
     */
    private LocalDate relievingDate;

    /**
     * Whether active.
     */
    private Boolean active;

    /**
     * Whether API user.
     */
    private Boolean apiUser;

    private List<String> roles;

    /**
     * Additional dynamic attributes.
     */
    private Map<String, Object> attributes;

    private AuditInfo audit;

    // ── Search indexing ───────────────────────────────────────────────────────
    // Mirrors the legacy touidx/touweb pattern (each domain model owns a
    // @JsonIgnore getSearchObject()-style method) -- see SearchIndexable.

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return "USER";
    }

    @JsonIgnore
    @Override
    public String getSearchModelKey() {
        return id;
    }

    @JsonIgnore
    @Override
    public Map<String, Object> getSearchIndexFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("ownerKey", tenantId);
        fields.put("tenantId", tenantId);
        fields.put("text1", userName);
        fields.put("text2", email);
        fields.put("text3", phoneNumber != null ? phoneNumber.getConcatString() : null);
        fields.put("text4", status != null ? status.name() : null);
        fields.put("text5", tenantId);
        fields.put("text6", displayName);
        fields.put("text7", fullName != null ? fullName.getFullName() : null);
        fields.put("text8", roles != null && !roles.isEmpty() ? String.join(",", roles) : null);
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
