package com.heuristiq.uam.organization.operatingmodel.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.enums.UamEnums.AuditAction;
import com.heuristiq.uam.enums.UamEnums.AuditStatus;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelConstants;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Every operating-model change is a domain audit event with the schedule before and after; auditing never fails the change. */
class AuditLogOperatingModelAuditTest {

    @Test
    void recordsTheChangeWithBeforeAndAfter() {
        AuditLogServiceUseCase logs = mock(AuditLogServiceUseCase.class);
        List<Map<String, String>> after = List.of(Map.of("model", "FRANCHISE", "effectiveFrom", "2026-12-01"));

        new AuditLogOperatingModelAudit(logs).scheduleChanged("acme", "admin-1", List.of(), after);

        ArgumentCaptor<AuditLogServiceUseCase.RecordInput> captor = ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(logs).record(captor.capture());
        AuditLog entry = captor.getValue().entry();
        assertThat(entry.getTenantId()).isEqualTo("acme");
        assertThat(entry.getEntityId()).isEqualTo("acme");
        assertThat(entry.getUserId()).isEqualTo("admin-1");
        assertThat(entry.getEntityType()).isEqualTo(OperatingModelConstants.AUDIT_ENTITY_TYPE);
        assertThat(entry.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(entry.getStatus()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(entry.getOldValue()).isEqualTo(Map.of(OperatingModelConstants.SCHEDULE_FIELD, List.of()));
        assertThat(entry.getNewValue()).isEqualTo(Map.of(OperatingModelConstants.SCHEDULE_FIELD, after));
    }

    @Test
    void aFailedAuditWriteDoesNotFailTheChange() {
        AuditLogServiceUseCase logs = mock(AuditLogServiceUseCase.class);
        when(logs.record(any())).thenThrow(new IllegalStateException("outbox down"));

        assertThatCode(() -> new AuditLogOperatingModelAudit(logs).scheduleChanged("acme", "admin-1", List.of(), List.of()))
                .doesNotThrowAnyException();
    }
}
