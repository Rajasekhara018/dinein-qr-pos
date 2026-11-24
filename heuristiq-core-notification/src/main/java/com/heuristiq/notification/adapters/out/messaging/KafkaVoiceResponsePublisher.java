package com.railcab.nms.adapters.out.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.nms.application.dto.VoiceResponseEvent;
import com.railcab.nms.application.port.out.VoiceResponsePublisher;
import com.railcab.nms.infrastructure.config.NmsProperties;

import lombok.RequiredArgsConstructor;

/**
 * Publishes voice call answers as JSON on {@code nms.kafka.topic.voice-responses} ({@code nms-voice-responses}), keyed by
 * the entity id so every answer for one ride stays in order on one partition. Waits for the broker's acknowledgement so a
 * lost publish is reported back (the provider callback then fails and the provider retries).
 */
@Component
@RequiredArgsConstructor
public class KafkaVoiceResponsePublisher implements VoiceResponsePublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaVoiceResponsePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final NmsProperties props;

    @Override
    public boolean publish(VoiceResponseEvent event) {
        String topic = props.getKafka().getTopic().getVoiceResponses();
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.entityId(), json)
                    .get(MessagingAdapterConstants.VOICE_RESPONSE_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (JsonProcessingException e) {
            logger.error("Voice response {} could not be serialised: {}", event.eventId(), e.getOriginalMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Publishing voice response {} was interrupted", event.eventId());
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // Boundary to the broker: any failure means "not published", reported to the caller.
            logger.error("Publishing voice response {} to {} failed: {}", event.eventId(), topic, e.getClass().getSimpleName());
            return false;
        }
    }
}
