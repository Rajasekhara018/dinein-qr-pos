package com.heuristiq.identity.pos.adapter.out;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.enums.UamEnums.AuditAction;
import com.heuristiq.identity.enums.UamEnums.AuditStatus;
import com.heuristiq.identity.pos.application.port.PosAudit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Writes POS changes to UAM audit log (central audit outbox). A failed write is logged and never fails the change. */
public class AuditLogPosAudit implements PosAudit {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPosAudit.class);

    private final AuditLogServiceUseCase auditLogs;

    public AuditLogPosAudit(AuditLogServiceUseCase auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void changed(String tenantId, String actorUserId, String entityType, String entityId, Map<String, Object> before,
            Map<String, Object> after) {
        try {
            auditLogs.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(actorUserId)
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(before == null ? AuditAction.CREATE : AuditAction.UPDATE)
                    .status(AuditStatus.SUCCESS)
                    .oldValue(before)
                    .newValue(after)
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException e) {
            log.warn("pos.audit.failed tenant={} entity={} reason={}", tenantId, entityType, e.toString());
        }
    }
}
