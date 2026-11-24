package com.heuristiq.uam.application.access;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model: permission for a user, or for everyone holding a role, to sign in to a registered
 * {@code Application}. See {@code persistence.mongo.ApplicationAccessDocument} / {@code persistence.jpa.ApplicationAccessEntity}.
 * A grant is never deleted when withdrawn; it is marked revoked so the history stays readable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationAccess {

    private String id;

    /** Code of the registered application, stored upper-case. */
    private String applicationCode;

    private AccessSubjectType subjectType;

    /** User id for {@link AccessSubjectType#USER}, role name (upper-case) for {@link AccessSubjectType#ROLE}. */
    private String subjectId;

    @Builder.Default
    private boolean active = true;

    private String note;

    private String grantedBy;

    private LocalDateTime grantedAt;

    private String revokedBy;

    private LocalDateTime revokedAt;
}
