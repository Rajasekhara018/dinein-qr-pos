package com.heuristiq.identity.organization.operatingmodel.adapter.out;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.enums.UamEnums.AuditAction;
import com.heuristiq.identity.enums.UamEnums.AuditStatus;
import com.heuristiq.identity.organization.operatingmodel.application.port.OperatingModelAudit;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelConstants;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Writes operating-model changes to UAM's audit log (central audit outbox). A failed write is logged and never fails the change. */
public class AuditLogOperatingModelAudit implements OperatingModelAudit {

    private static final Logger log = LoggerFactory.getLogger(AuditLogOperatingModelAudit.class);

    private final AuditLogServiceUseCase auditLogs;

    public AuditLogOperatingModelAudit(AuditLogServiceUseCase auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void scheduleChanged(String organizationId, String actorUserId, List<Map<String, String>> before,
                                List<Map<String, String>> after) {
        try {
            auditLogs.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(organizationId)
                    .userId(actorUserId)
                    .entityType(OperatingModelConstants.AUDIT_ENTITY_TYPE)
                    .entityId(organizationId)
                    .action(AuditAction.UPDATE)
                    .status(AuditStatus.SUCCESS)
                    .oldValue(Map.of(OperatingModelConstants.SCHEDULE_FIELD, before))
                    .newValue(Map.of(OperatingModelConstants.SCHEDULE_FIELD, after))
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException e) {
            log.warn("operating-model.audit.failed organization={} reason={}", organizationId, e.toString());
        }
    }
}
