package com.heuristiq.identity.orgstructure.adapter.out;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.enums.UamEnums.AuditAction;
import com.heuristiq.identity.enums.UamEnums.AuditStatus;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportAudit;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportConstants;

/** Writes the department import domain audit events to UAM's audit log. A failed write is logged and never fails the import. */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class AuditLogDepartmentImportAudit implements DepartmentImportAudit {
    private static final Logger log = LoggerFactory.getLogger(AuditLogDepartmentImportAudit.class);

    private final AuditLogServiceUseCase auditLogs;

    AuditLogDepartmentImportAudit(AuditLogServiceUseCase auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void record(String tenantId, String actor, String batchId, boolean success, String fileName, int totalRows, int validRows,
                       int created, int updated, String message) {
        try {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("fileName", fileName);
            after.put("totalRows", totalRows);
            after.put("validRows", validRows);
            after.put("created", created);
            after.put("updated", updated);
            auditLogs.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(actor)
                    .entityType(DepartmentImportConstants.AUDIT_ENTITY_TYPE)
                    .entityId(batchId)
                    .action(AuditAction.IMPORT)
                    .status(success ? AuditStatus.SUCCESS : AuditStatus.FAILURE)
                    .newValue(after)
                    .remarks(message)
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException e) {
            log.warn("org.department-import.audit.failed tenant={} import={} reason={}", tenantId, batchId, e.toString());
        }
    }
}
