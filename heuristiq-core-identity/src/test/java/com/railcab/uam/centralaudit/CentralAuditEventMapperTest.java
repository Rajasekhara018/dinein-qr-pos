package com.heuristiq.uam.centralaudit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.enums.UamEnums.AuditAction;
import com.heuristiq.uam.enums.UamEnums.AuditStatus;

/** The UAM entry to central event mapping, including the values the audit service would otherwise reject. */
class CentralAuditEventMapperTest {
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void mapsAFullEntryToADomainEvent() {
        AuditLog entry = AuditLog.builder().tenantId("org-1").userId("u1").username("jane").entityType("auth-session")
                .entityId("u1").action(AuditAction.LOGIN).status(AuditStatus.SUCCESS).remarks("Login succeeded")
                .ipAddress("10.0.0.1").userAgent("UA").correlationId("c-1").requestId("r-1").build();

        CentralAuditEvent event = CentralAuditEventMapper.toEvent(entry, NOW);

        assertThat(event.eventId()).hasSizeBetween(8, 64);
        assertThat(event.eventType()).isEqualTo("DOMAIN");
        assertThat(event.sourceService()).isEqualTo("rider-uam");
        assertThat(event.feature()).isEqualTo("uam");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.tenantId()).isEqualTo("org-1");
        assertThat(event.userId()).isEqualTo("u1");
        assertThat(event.subjectType()).isEqualTo("AUTH_SESSION");
        assertThat(event.subjectId()).isEqualTo("u1");
        assertThat(event.reasonCode()).isEqualTo("LOGIN");
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.correlationId()).isEqualTo("c-1");
        assertThat(event.details()).containsEntry("action", "LOGIN").containsEntry("username", "jane")
                .containsEntry("remarks", "Login succeeded");
    }

    @Test
    void aFailureStatusIsAFailureOutcomeAndAWarningStaysASuccessWithItsStatusInDetails() {
        assertThat(CentralAuditEventMapper.toEvent(AuditLog.builder().status(AuditStatus.FAILURE).build(), NOW).outcome())
                .isEqualTo("FAILURE");
        CentralAuditEvent warning = CentralAuditEventMapper.toEvent(AuditLog.builder().status(AuditStatus.WARNING).build(), NOW);
        assertThat(warning.outcome()).isEqualTo("SUCCESS");
        assertThat(warning.details()).containsEntry("status", "WARNING");
    }

    @Test
    void malformedSubjectValuesAreLeftOutInsteadOfRejectingTheEvent() {
        AuditLog entry = AuditLog.builder().entityType("123 !!").entityId("has spaces & symbols").build();

        CentralAuditEvent event = CentralAuditEventMapper.toEvent(entry, NOW);

        assertThat(event.subjectType()).isNull();
        assertThat(event.subjectId()).isNull();
        assertThat(event.reasonCode()).isNull();
    }

    @Test
    void beforeAndAfterValuesAreFlattenedWithSafeKeysAndCapped() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("role name", "ADMIN");
        after.put("nothing", null);
        for (int i = 0; i < 30; i++) {
            after.put("k" + i, "v" + i);
        }
        after.put("long", "x".repeat(900));
        AuditLog entry = AuditLog.builder().oldValue(Map.of("status", "PENDING")).newValue(after).build();

        Map<String, Object> details = CentralAuditEventMapper.toEvent(entry, NOW).details();

        assertThat(details).containsEntry("before.status", "PENDING").containsEntry("after.role_name", "ADMIN");
        assertThat(details).doesNotContainKey("after.nothing").hasSize(20);
        assertThat(CentralAuditEventMapper.toEvent(AuditLog.builder().newValue(Map.of("long", "x".repeat(900))).build(), NOW)
                .details().get("after.long").toString()).hasSize(500);
    }
}
