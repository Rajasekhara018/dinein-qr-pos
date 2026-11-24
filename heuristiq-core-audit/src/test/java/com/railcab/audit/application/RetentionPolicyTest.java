package com.railcab.audit.application;

import com.railcab.audit.config.AuditProperties;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionPolicyTest {

    private final AuditProperties props = new AuditProperties();
    private final RetentionPolicy policy = new RetentionPolicy(props);

    private static AuditEvent event(AuditEventType type, String method) {
        return AuditEvent.builder().eventType(type).method(method).build();
    }

    @Test
    void defaultsPerClass() {
        assertThat(policy.daysFor(event(AuditEventType.ACCESS, "GET"))).isEqualTo(180);
        assertThat(policy.daysFor(event(AuditEventType.ACCESS, "HEAD"))).isEqualTo(180);
        assertThat(policy.daysFor(event(AuditEventType.SCREEN_VIEW, null))).isEqualTo(180);
        assertThat(policy.daysFor(event(AuditEventType.ACCESS, "POST"))).isEqualTo(180);
        assertThat(policy.daysFor(event(AuditEventType.ACCESS, "DELETE"))).isEqualTo(180);
        assertThat(policy.daysFor(event(AuditEventType.ACCESS, null))).isEqualTo(180);
        assertThat(policy.daysFor(event(AuditEventType.COMMUNICATION, null))).isEqualTo(365);
        assertThat(policy.daysFor(event(AuditEventType.PAYMENT_GATEWAY, null))).isEqualTo(365);
        assertThat(policy.daysFor(event(AuditEventType.DOMAIN, null))).isEqualTo(365);
    }

    @Test
    void retentionIsConfigurableAndCountedFromTheReceiveTime() {
        props.getRetention().setAccessReadDays(7);
        Instant received = Instant.parse("2026-10-06T10:00:00Z");
        assertThat(policy.expireAt(event(AuditEventType.ACCESS, "GET"), received)).isEqualTo(received.plus(Duration.ofDays(7)));
    }
}
