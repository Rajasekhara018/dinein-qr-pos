package com.railcab.nms.adapters.in.messaging;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.railcab.nms.adapters.in.OriginHeaders;
import com.railcab.nms.application.port.in.NotificationDispatchUseCase;
import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.shared.CommonRequestMapper;
import com.railcab.nms.shared.CommonRequestObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Consumes {@link NotificationRequest} JSON messages from Kafka and delegates
 * to {@link NotificationDispatchUseCase}.
 *
 * <p>Other microservices (booking-service, payment-api, etc.) publish to
 * {@code railcab-notification-events} whenever they need to notify a user.
 */
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationKafkaConsumer.class);

    private final NotificationDispatchUseCase dispatchService;
    private final ObjectMapper objectMapper;
    private final CommonRequestMapper requestMapper;

    @KafkaListener(
            topics = "${nms.kafka.topic.notification-events:railcab-notification-events}",
            groupId = "${spring.kafka.consumer.group-id:railcab-nms-group}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        logger.info("NotificationKafkaConsumer: received message");
        try {
            NotificationRequest request = readNotificationRequest(record.value());
            applyOriginHeader(request, record);
            logger.info("NotificationKafkaConsumer: dispatching event={}", request.getEvent());
            var results = dispatchService.dispatch(request);
            if (results.isEmpty() || results.stream().noneMatch(result -> result.success())) {
                throw new IllegalStateException("No notification channel delivered successfully");
            }
        } catch (Exception e) {
            logger.error("NotificationKafkaConsumer: failed to process message: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to process notification event", e);
        }
    }

    /**
     * Back-compatible origin: producers that have not yet put {@code sourceService} in the payload can
     * name themselves in the {@code X-Source-Service} Kafka header. The payload value wins when both are set.
     */
    private void applyOriginHeader(NotificationRequest request, ConsumerRecord<String, String> record) {
        if (StringUtils.hasText(request.getSourceService())) {
            return;
        }
        Header header = record.headers().lastHeader(OriginHeaders.SOURCE_SERVICE);
        if (header != null && header.value() != null) {
            request.setSourceService(new String(header.value(), StandardCharsets.UTF_8));
        }
    }

    private NotificationRequest readNotificationRequest(String message) throws Exception {
        CommonRequestObject commonRequest = objectMapper.readValue(message, CommonRequestObject.class);
        if (commonRequest.getRequestObject() == null) {
            return objectMapper.readValue(message, NotificationRequest.class);
        }
        NotificationRequest request = requestMapper.requireRequestObject(commonRequest, NotificationRequest.class);
        if (commonRequest.getMessageID() != null
                && (request.getMessageId() == null || request.getMessageId().isBlank())) {
            request.setMessageId(commonRequest.getMessageID());
        }
        if (!StringUtils.hasText(request.getCorrelationId())) {
            request.setCorrelationId(StringUtils.hasText(commonRequest.getMessageID())
                    ? commonRequest.getMessageID()
                    : request.getMessageId());
        }
        return request;
    }
}
