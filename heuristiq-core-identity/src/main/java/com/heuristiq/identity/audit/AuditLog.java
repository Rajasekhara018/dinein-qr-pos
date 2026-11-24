package com.heuristiq.uam.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.heuristiq.uam.enums.UamEnums.AuditAction;
import com.heuristiq.uam.enums.UamEnums.AuditStatus;
import com.heuristiq.uam.helper.FieldChange;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code audit.persistence.mongo.AuditLogDocument} / {@code audit.persistence.jpa.AuditLogEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private String id;

    private String tenantId;

    private String applicationId;

    private String userId;

    private String username;

    private String entityType;

    private String entityId;

    private AuditAction action;

    private AuditStatus status;

    private Map<String,Object> oldValue;

    private Map<String,Object> newValue;

    private List<FieldChange> changes;

    private String remarks;

    private String ipAddress;

    private String userAgent;

    private String device;

    private String browser;

    private String operatingSystem;

    private String correlationId;

    private String requestId;

    private Long executionTimeMs;

    private LocalDateTime createdAt;
}
