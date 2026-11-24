package com.heuristiq.uam.role.persistance.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.uam.enums.UamEnums.AssignmentType;
import com.heuristiq.uam.helper.AuditInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "uam_roles")
public class UamRoleDocument {
    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed(unique = true)
    private String code;

    private String name;

    private String description;

    @Indexed
    private Boolean active;

    @Indexed
    private Boolean systemRole;

    private Boolean editable;

    private List<String> permissions;

    @Indexed
    private String assignedBy;

    private String assignmentReason;

    @Indexed
    private AssignmentType assignmentType;

    @Indexed
    private LocalDateTime effectiveFrom;

    @Indexed
    private LocalDateTime effectiveUntil;

    @Builder.Default
    private Boolean defaultRole = false;

    private Map<String, Object> attributes;

    private AuditInfo audit;
}
