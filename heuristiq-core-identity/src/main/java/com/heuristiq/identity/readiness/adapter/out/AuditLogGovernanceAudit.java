package com.heuristiq.identity.readiness.adapter.out;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.enums.UamEnums.AuditAction;
import com.heuristiq.identity.enums.UamEnums.AuditStatus;
import com.heuristiq.identity.readiness.application.port.GovernanceAudit;
import com.heuristiq.identity.readiness.domain.GovernanceConstants;

/** Writes the go-live domain audit events to UAM's audit log. A failed write is logged and never fails the action. */
public class AuditLogGovernanceAudit implements GovernanceAudit {

    private static final Logger log = LoggerFactory.getLogger(AuditLogGovernanceAudit.class);

    private final AuditLogServiceUseCase auditLogs;

    public AuditLogGovernanceAudit(AuditLogServiceUseCase auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void record(String tenantId, String userId, String entityId, boolean overridden, Map<String, Object> before,
                       Map<String, Object> after, String remarks) {
        try {
            auditLogs.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .entityType(GovernanceConstants.AUDIT_ENTITY_TYPE)
                    .entityId(entityId)
                    .action(overridden ? AuditAction.EXECUTE : AuditAction.REJECT)
                    .status(overridden ? AuditStatus.WARNING : AuditStatus.FAILURE)
                    .oldValue(before)
                    .newValue(after)
                    .remarks(remarks)
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException e) {
            log.warn("readiness.audit.failed tenant={} entity={} reason={}", tenantId, entityId, e.toString());
        }
    }
}
