package com.heuristiq.uam.organization.legalprofile.adapter.out;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.enums.UamEnums.AuditAction;
import com.heuristiq.uam.enums.UamEnums.AuditStatus;
import com.heuristiq.uam.organization.legalprofile.application.port.LegalProfileAudit;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileConstants;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Writes legal-profile changes to UAM's audit log (central audit outbox). A failed write is logged and never fails the change. */
public class AuditLogLegalProfileAudit implements LegalProfileAudit {

    private static final Logger log = LoggerFactory.getLogger(AuditLogLegalProfileAudit.class);

    private final AuditLogServiceUseCase auditLogs;

    public AuditLogLegalProfileAudit(AuditLogServiceUseCase auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void profileChanged(String organizationId, String actorUserId, Map<String, Object> before, Map<String, Object> after) {
        try {
            auditLogs.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(organizationId)
                    .userId(actorUserId)
                    .entityType(LegalProfileConstants.AUDIT_ENTITY_TYPE)
                    .entityId(organizationId)
                    .action(AuditAction.UPDATE)
                    .status(AuditStatus.SUCCESS)
                    .oldValue(before)
                    .newValue(after)
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException e) {
            log.warn("legal-profile.audit.failed organization={} reason={}", organizationId, e.toString());
        }
    }
}
