package com.heuristiq.identity.orgstructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.enums.UamEnums.AuditAction;
import com.heuristiq.identity.enums.UamEnums.AuditStatus;

/** The audit entry written when a department import ends, and that an audit outage never fails the import. */
class AuditLogDepartmentImportAuditTest {
    private final AuditLogServiceUseCase auditLogs = mock(AuditLogServiceUseCase.class);
    private final AuditLogDepartmentImportAudit audit = new AuditLogDepartmentImportAudit(auditLogs);

    @Test
    void anAppliedImportIsRecordedAsASuccessWithItsCounts() {
        audit.record("org-1", "admin", "batch-1", true, "d.csv", 10, 9, 7, 2, null);

        ArgumentCaptor<AuditLogServiceUseCase.RecordInput> input = ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(auditLogs).record(input.capture());
        AuditLog entry = input.getValue().entry();
        assertThat(entry.getTenantId()).isEqualTo("org-1");
        assertThat(entry.getUserId()).isEqualTo("admin");
        assertThat(entry.getEntityId()).isEqualTo("batch-1");
        assertThat(entry.getAction()).isEqualTo(AuditAction.IMPORT);
        assertThat(entry.getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    void aFailedImportIsRecordedAsAFailureWithTheReason() {
        audit.record("org-1", "admin", "batch-2", false, "d.csv", 10, 9, 0, 0, "boom");

        ArgumentCaptor<AuditLogServiceUseCase.RecordInput> input = ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(auditLogs).record(input.capture());
        assertThat(input.getValue().entry().getStatus()).isEqualTo(AuditStatus.FAILURE);
        assertThat(input.getValue().entry().getRemarks()).isEqualTo("boom");
    }

    @Test
    void anAuditOutageDoesNotFailTheImport() {
        doThrow(new IllegalStateException("down")).when(auditLogs).record(any());

        assertThatCode(() -> audit.record("org-1", "admin", "b", true, "d.csv", 1, 1, 1, 0, null)).doesNotThrowAnyException();
    }
}
