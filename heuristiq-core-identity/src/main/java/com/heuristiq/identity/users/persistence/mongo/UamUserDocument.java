package com.heuristiq.uam.users.persistence.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.uam.enums.UamEnums.Gender;
import com.heuristiq.uam.enums.UamEnums.LanguageEnum;
import com.heuristiq.uam.enums.UamEnums.UserStatus;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.helper.RPhoneNumber;
import com.heuristiq.uam.helper.RUpload;
import com.heuristiq.uam.util.ImmutableAfterCreate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "uam_users")
@CompoundIndexes({
    // A single-field @Indexed on the whole `phoneNumber` embedded object would index the
    // entire subdocument, not the `number` path -- it can't serve findByPhoneNumber_Number
    // (used on every phone/OTP login attempt), so this indexes the nested path directly.
    // One account per REAL number: unique on the canonical E.164 value, only for documents that have one. The old
    // unique index on the national number alone (uk_phone_number) is dropped at startup by UamUserMongoPhoneMigration.
    @CompoundIndex(name = "uk_phone_e164", def = "{'phoneE164':1}", unique = true,
            partialFilter = "{'phoneE164': {$type: 'string'}}")
})
public class UamUserDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    @ImmutableAfterCreate
    @Indexed(unique = true)
    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50)
    private String userName;

    @Indexed(unique = true, sparse = true)
    @Email(message = "Invalid email address")
    @Size(max = 150)
    private String email;

    @NotNull(message = "Phone number is required")
    private RPhoneNumber phoneNumber;

    /** Canonical E.164 form of {@link #phoneNumber}; unique where present (one account per real number). Null is never written. */
    private String phoneE164;

    @ImmutableAfterCreate
    @Indexed(unique = true, sparse = true)
    @Size(max = 50)
    private String employeeCode;

    @Size(max = 200)
    private String displayName;

    @Size(max = 250)
    private RName fullName;

    @NotNull
    @Indexed
    private UserStatus status;

    @Indexed
    private String departmentId;

    @Indexed
    private String reportingManagerId;

    @Indexed
    private String designationId;

    @Indexed
    private String locationId;

    @Size(max = 50)
    private String timezone;

    private LanguageEnum preferredLanguage;

    private RUpload profileImage;

    private Gender gender;

    @Indexed
    private LocalDate joiningDate;

    private LocalDate relievingDate;

    @Indexed
    private Boolean active;

    @Indexed
    private Boolean apiUser;

    private List<String> roles;

    private Map<String, Object> attributes;

    private AuditInfo audit;
}
