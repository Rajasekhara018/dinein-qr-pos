package com.heuristiq.identity.application.access.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.identity.application.access.AccessSubjectType;

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
@Document(collection = "application_access")
@CompoundIndexes({
    @CompoundIndex(name = "uk_application_access_subject", def = "{'applicationCode':1,'subjectType':1,'subjectId':1}", unique = true),
    @CompoundIndex(name = "idx_application_access_subject", def = "{'subjectType':1,'subjectId':1,'active':1}")
})
public class ApplicationAccessDocument {

    @Id
    private String id;

    @NotBlank
    @Size(max = 100)
    private String applicationCode;

    private AccessSubjectType subjectType;

    @NotBlank
    @Size(max = 100)
    private String subjectId;

    private boolean active;

    @Size(max = 500)
    private String note;

    private String grantedBy;

    private LocalDateTime grantedAt;

    private String revokedBy;

    private LocalDateTime revokedAt;
}
