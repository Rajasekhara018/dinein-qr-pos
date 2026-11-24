package com.heuristiq.payment.gatewayaudit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.payment.gatewayaudit.model.WebhookOutcome;
import com.heuristiq.payment.gatewayaudit.repository.PaymentAuditOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The audit event carries the organization of the payment it is about, so the audit screen can separate organizations. */
class AuditOutboxWriterTenantTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AuditOutboxWriter writer = new AuditOutboxWriter(mock(PaymentAuditOutboxRepository.class), mapper, CLOCK);

    private JsonNode json(String tenantId) throws Exception {
        return mapper.readTree(writer.toJson(new GatewayAuditEvent("evt-1", CLOCK.instant(), "SUCCESS", 200, 5L, "corr-1",
                Map.of("gateway", "RAZORPAY"), tenantId)));
    }

    @Test
    void theOrganizationIsSentWhenThereIsOne() throws Exception {
        assertThat(json("org-7").get("tenantId").asText()).isEqualTo("org-7");
    }

    @Test
    void noOrganizationMeansNoFieldSoTheRecordIsLegacy() throws Exception {
        assertThat(json(null).has("tenantId")).isFalse();
        assertThat(json("  ").has("tenantId")).isFalse();
    }

    @Test
    void aWebhookObservationWithoutAnOrganizationStillBuildsTheOldWay() {
        WebhookObservation old = new WebhookObservation("RAZORPAY", "payment.captured", "evt-2", true, WebhookOutcome.PROCESSED, "pay-1");

        assertThat(old.tenantId()).isNull();
    }
}
