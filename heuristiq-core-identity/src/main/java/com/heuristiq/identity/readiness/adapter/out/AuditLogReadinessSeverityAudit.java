package com.heuristiq.uam.readiness.adapter.out;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.enums.UamEnums.AuditAction;
import com.heuristiq.uam.enums.UamEnums.AuditStatus;
import com.heuristiq.uam.readiness.application.port.ReadinessSeverityAudit;
import com.heuristiq.uam.readiness.domain.GovernanceConstants;

/** Writes the severity-change domain audit events to UAM's audit log. A failed write is logged and never fails the action. */
public class AuditLogReadinessSeverityAudit implements ReadinessSeverityAudit {

    private static final Logger log = LoggerFactory.getLogger(AuditLogReadinessSeverityAudit.class);

    private final AuditLogServiceUseCase auditLogs;

    public AuditLogReadinessSeverityAudit(AuditLogServiceUseCase auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void record(String tenantId, String userId, String entityId, Map<String, Object> before, Map<String, Object> after,
                       String remarks) {
        try {
            auditLogs.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .entityType(GovernanceConstants.AUDIT_ENTITY_TYPE)
                    .entityId(entityId)
                    .action(AuditAction.UPDATE)
                    .status(AuditStatus.SUCCESS)
                    .oldValue(before)
                    .newValue(after)
                    .remarks(remarks)
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException e) {
            log.warn("readiness.severity.audit-failed tenant={} entity={} reason={}", tenantId, entityId, e.toString());
        }
    }
}
