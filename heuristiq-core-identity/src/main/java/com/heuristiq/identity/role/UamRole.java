package com.heuristiq.uam.role;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.heuristiq.uam.enums.UamEnums.AssignmentType;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.search.SearchIndexable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure domain model -- see {@code persistence.mongo.UamRoleDocument} / {@code persistence.jpa.UamRoleEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UamRole implements SearchIndexable {
    private String id;

    /**
     * Tenant.
     * Null = Global Group
     */
    private String tenantId;

    private String code;

    private String name;

    private String description;

    private Boolean active;

    private Boolean systemRole;

    private Boolean editable;

    private List<String> permissions;

    /**
     * Assigned By.
     */
    private String assignedBy;

    /**
     * Assignment Reason.
     */
    private String assignmentReason;

    /**
     * Assignment Type.
     */
    private AssignmentType assignmentType;

    /**
     * Effective From.
     */
    private LocalDateTime effectiveFrom;

    /**
     * Effective Until.
     */
    private LocalDateTime effectiveUntil;

    /**
     * Default Role.
     */
    @Builder.Default
    private Boolean defaultRole = false;

    private Map<String, Object> attributes;

    private AuditInfo audit;

    // ── Search indexing ───────────────────────────────────────────────────────
    // Mirrors the legacy touidx/touweb pattern (each domain model owns a
    // @JsonIgnore getSearchObject()-style method) -- see SearchIndexable.

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return "ROLE";
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
        fields.put("text1", code);
        fields.put("text2", name);
        fields.put("text3", description);
        fields.put("text4", tenantId);
        fields.put("text5", active != null ? active.toString() : null);
        fields.put("text6", assignmentType != null ? assignmentType.name() : null);
        fields.put("text7", permissions != null && !permissions.isEmpty() ? String.join(",", permissions) : null);
        fields.put("text8", systemRole != null ? systemRole.toString() : null);
        if (effectiveFrom != null) {
            fields.put("date1", effectiveFrom.atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        if (effectiveUntil != null) {
            fields.put("date2", effectiveUntil.atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
