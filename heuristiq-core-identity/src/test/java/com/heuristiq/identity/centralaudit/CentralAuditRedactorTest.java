package com.heuristiq.identity.centralaudit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.audit.AuditLog;

/** Secrets, e-mail and phone numbers never reach the outbox payload. */
class CentralAuditRedactorTest {

    @Test
    void secretKeysAreMaskedAndContactsInTextAreMasked() {
        assertThat(CentralAuditRedactor.redactValue("password", "hunter2")).isEqualTo("***");
        assertThat(CentralAuditRedactor.redactValue("otpCode", "123456")).isEqualTo("***");
        assertThat(CentralAuditRedactor.redactValue("refreshToken", "abc")).isEqualTo("***");
        assertThat(CentralAuditRedactor.redactValue("email", "jane@example.com")).isEqualTo("***");
        assertThat(CentralAuditRedactor.redactValue("mobile", "+91 98765 43210")).isEqualTo("***");
        assertThat(CentralAuditRedactor.redactValue("name", "Jane")).isEqualTo("Jane");
        assertThat(CentralAuditRedactor.redactValue("name", null)).isNull();
        assertThat(CentralAuditRedactor.text("x".repeat(900))).hasSize(500);
    }

    @Test
    void theMapperRedactsBeforeAndAfterSnapshotsRemarksAndUsername() {
        AuditLog entry = AuditLog.builder().username("jane@example.com").remarks("called 9876543210 about reset")
                .oldValue(Map.of("passwordHash", "$2a$secret", "status", "ACTIVE"))
                .newValue(Map.of("otp", "654321", "contact", "bob@example.com", "accessToken", "t")).build();

        Map<String, Object> details = CentralAuditEventMapper.toEvent(entry, Instant.parse("2026-11-02T10:00:00Z")).details();

        assertThat(details).containsEntry("before.passwordHash", "***").containsEntry("before.status", "ACTIVE")
                .containsEntry("after.otp", "***").containsEntry("after.contact", "***").containsEntry("after.accessToken", "***")
                .containsEntry("username", "***");
        assertThat(String.valueOf(details.get("remarks"))).doesNotContain("9876543210");
        assertThat(details.values().toString()).doesNotContain("hunter").doesNotContain("654321").doesNotContain("bob@example.com");
    }
}
