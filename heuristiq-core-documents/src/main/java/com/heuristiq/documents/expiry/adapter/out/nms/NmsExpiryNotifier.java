package com.heuristiq.documents.expiry.adapter.out.nms;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.heuristiq.documents.document.domain.DocumentValidityConstants;
import com.heuristiq.documents.expiry.application.ExpiryNotifier;
import com.heuristiq.documents.expiry.config.DocumentExpiryProperties;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;

/**
 * Sends the expiry reminders through NMS ({@code POST /api/v1/nms/notify}) as IN_APP, FCM_PUSH and SMS messages to the owner (the driver's
 * user id), with the origin (source service, event code, organization, document) so each can be traced. The message id is fixed per
 * document, date and reminder, so NMS drops a repeat and the call is {@link Idempotency#SAFE}.
 */
@Component
class NmsExpiryNotifier implements ExpiryNotifier {

    private static final Logger log = LoggerFactory.getLogger(NmsExpiryNotifier.class);

    private final WebClient client;
    private final ResilientCaller caller;
    private final String apiKey;

    NmsExpiryNotifier(@Qualifier(ExpiryNmsConstants.NMS_WEB_CLIENT_BEAN) WebClient client,
                      @Qualifier(ExpiryNmsConstants.NMS_RESILIENT_CALLER_BEAN) ResilientCaller caller, DocumentExpiryProperties properties) {
        this.client = client;
        this.caller = caller;
        this.apiKey = properties.nms().internalApiKey();
    }

    @Override
    public boolean notify(String tenantId, String ownerId, String documentId, String category, LocalDate validUntil, long daysLeft,
                          int daysBefore) {
        String event = daysBefore == DocumentValidityConstants.LAPSED_NOTICE ? ExpiryNmsConstants.EVENT_LAPSED : ExpiryNmsConstants.EVENT_EXPIRING;
        Notification body = new Notification(String.format(ExpiryNmsConstants.MESSAGE_ID_FORMAT, event, documentId, validUntil, daysBefore),
                new Request(event, Map.of(ExpiryNmsConstants.VAR_CATEGORY, category,
                        ExpiryNmsConstants.VAR_DOCUMENT_NAME, ExpiryNmsConstants.DOCUMENT_NAMES.getOrDefault(category, category), ExpiryNmsConstants.VAR_VALID_UNTIL, validUntil.toString(),
                        ExpiryNmsConstants.VAR_DAYS_LEFT, Long.toString(Math.max(daysLeft, 0))),
                        ExpiryNmsConstants.CHANNELS, ExpiryNmsConstants.SOURCE_SERVICE, event, tenantId, null,
                        ExpiryNmsConstants.ENTITY_TYPE, documentId, MDC.get(ExpiryNmsConstants.CORRELATION_MDC_KEY), List.of(ownerId)));
        try {
            caller.callBlocking(() -> {
                WebClient.RequestBodySpec request = client.post().uri(ExpiryNmsConstants.NOTIFY_PATH);
                if (apiKey != null && !apiKey.isBlank()) {
                    request.header(ExpiryNmsConstants.API_KEY_HEADER, apiKey);
                }
                return request.contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().bodyToMono(String.class).defaultIfEmpty("");
            }, Idempotency.SAFE);
            return true;
        } catch (RuntimeException e) {
            log.warn("Expiry reminder not sent: event={}, document={}, reason={}", event, documentId,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /** NMS's body: the message id (its idempotency key) around the request. */
    record Notification(String messageID, Request requestObject) {
    }

    /** The notification; {@code userIds} are the people an IN_APP or FCM_PUSH message goes to (NMS finds their devices). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(String event, Map<String, String> variables, List<String> channels, String sourceService, String eventCode,
                   String tenantId, String triggeringUserId, String entityType, String entityId, String correlationId,
                   List<String> userIds) {
    }
}
