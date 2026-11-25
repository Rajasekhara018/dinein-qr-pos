package com.heuristiq.notification.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.notification.application.dto.VoiceResponseEvent;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

class KafkaVoiceResponsePublisherTest {

    private static final VoiceResponseEvent EVENT = new VoiceResponseEvent("CA1_DIGITS-1", "RIDE", "ride-1",
            "EARLY_DROP_CONFIRM_CALL", "1", "ANSWERED", "2026-11-08T10:00:00Z", "default");

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final KafkaVoiceResponsePublisher publisher = new KafkaVoiceResponsePublisher(kafka, mapper, new NmsProperties());

    @Test
    void publishesTheContractJsonKeyedByTheRide() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        assertThat(publisher.publish(EVENT)).isTrue();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("nms-voice-responses"), eq("ride-1"), json.capture());
        JsonNode node = mapper.readTree(json.getValue());
        assertThat(node.path("eventId").asText()).isEqualTo("CA1_DIGITS-1");
        assertThat(node.path("entityType").asText()).isEqualTo("RIDE");
        assertThat(node.path("entityId").asText()).isEqualTo("ride-1");
        assertThat(node.path("eventCode").asText()).isEqualTo("EARLY_DROP_CONFIRM_CALL");
        assertThat(node.path("digit").asText()).isEqualTo("1");
        assertThat(node.path("outcome").asText()).isEqualTo("ANSWERED");
        assertThat(node.path("occurredAt").asText()).isEqualTo("2026-11-08T10:00:00Z");
    }

    @Test
    void aBrokerFailureIsReportedAsNotPublished() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(publisher.publish(EVENT)).isFalse();
    }

    @Test
    void aSendThatThrowsIsReportedAsNotPublished() {
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("no producer"));

        assertThat(publisher.publish(EVENT)).isFalse();
    }

    @Test
    void anInterruptedWaitIsReportedAndKeepsTheInterruptFlag() {
        CompletableFuture<SendResult<String, String>> never = new CompletableFuture<>();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(never);
        Thread.currentThread().interrupt();
        try {
            assertThat(publisher.publish(EVENT)).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
