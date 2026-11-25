package com.heuristiq.identity.users.persistence.jpa;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.identity.enums.UamEnums.Gender;
import com.heuristiq.identity.enums.UamEnums.LanguageEnum;
import com.heuristiq.identity.enums.UamEnums.UserStatus;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.RName;
import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.helper.RUpload;
import com.heuristiq.identity.util.JsonMapAttributeConverter;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Entity
@Table(name = "uam_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_uam_user_user_name", columnNames = "user_name"),
        @UniqueConstraint(name = "uk_uam_user_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_uam_user_phone_number", columnNames = "number")
})
public class UamUserEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String tenantId;

    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50)
    @Column(name = "user_name", unique = true)
    private String userName;

    @Email(message = "Invalid email address")
    @Size(max = 150)
    @Column(unique = true)
    private String email;

    @NotNull(message = "Phone number is required")
    @Embedded
    private RPhoneNumber phoneNumber;

    /** Canonical E.164 form of {@link #phoneNumber}; unique where not null (one account per real number). */
    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Size(max = 50)
    private String employeeCode;

    @Size(max = 200)
    private String displayName;

    @Embedded
    private RName fullName;

    @NotNull
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private String departmentId;

    private String reportingManagerId;

    private String designationId;

    private String locationId;

    @Size(max = 50)
    private String timezone;

    @Enumerated(EnumType.STRING)
    private LanguageEnum preferredLanguage;

    @Embedded
    private RUpload profileImage;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate joiningDate;

    private LocalDate relievingDate;

    private Boolean active;

    private Boolean apiUser;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "uam_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_code")
    @OrderColumn(name = "idx")
    private List<String> roles;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> attributes;

    @Embedded
    private AuditInfo audit;
}
