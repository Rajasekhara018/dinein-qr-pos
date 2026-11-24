package com.heuristiq.uam.application.access.persistence.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.application.access.AccessSubjectType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Entity
@Table(name = "application_access",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_access_subject",
                columnNames = {"application_code", "subject_type", "subject_id"}),
        indexes = {
                @Index(name = "idx_application_access_subject", columnList = "subject_type,subject_id,active"),
                @Index(name = "idx_application_access_application", columnList = "application_code")
        })
public class ApplicationAccessEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "application_code", nullable = false, length = 100)
    private String applicationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private AccessSubjectType subjectType;

    @NotBlank
    @Size(max = 100)
    @Column(name = "subject_id", nullable = false, length = 100)
    private String subjectId;

    @Column(nullable = false)
    private boolean active;

    @Size(max = 500)
    @Column(length = 500)
    private String note;

    @Column(name = "granted_by", length = 100)
    private String grantedBy;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "revoked_by", length = 100)
    private String revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
