package com.heuristq.dinein.payment;

import com.heuristq.dinein.payment.domain.PaymentEventEntity;
import com.heuristq.dinein.payment.domain.PaymentEventRepository;
import com.heuristq.dinein.payment.gateway.PaymentGateway;
import com.heuristq.dinein.payment.gateway.PaymentGatewayRegistry;
import com.heuristq.dinein.payment.gateway.WebhookParseResult;
import com.heuristq.dinein.payment.gateway.WebhookParseResult.GatewayEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Webhooks are the source of truth for payment state. Every event is logged in {@code payment_event}; valid events
 * are de-duplicated by (provider, event id) and processed idempotently. A stored-but-unprocessed event (for example
 * after a crash) is processed again when the provider retries.
 */
@Slf4j
@Service
public class WebhookService {

    public enum Result { PROCESSED, DUPLICATE, INVALID_SIGNATURE }

    private final PaymentGatewayRegistry gateways;
    private final PaymentEventRepository eventRepository;
    private final PaymentStateService paymentStateService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public WebhookService(PaymentGatewayRegistry gateways, PaymentEventRepository eventRepository,
                          PaymentStateService paymentStateService, TransactionTemplate tx, Clock clock) {
        this.gateways = gateways;
        this.eventRepository = eventRepository;
        this.paymentStateService = paymentStateService;
        this.tx = tx;
        this.clock = clock;
    }

    public Result handle(String providerCode, byte[] rawBody, Map<String, String> headers) {
        PaymentGateway gateway = gateways.get(providerCode);
        WebhookParseResult parsed = gateway.parseWebhook(rawBody, headers);
        if (!parsed.signatureValid()) {
            // Keep a record for audit, but never with the claimed event id (an attacker could squat real ids).
            tx.executeWithoutResult(s -> eventRepository.save(event(gateway.code(), null, parsed, false)));
            log.warn("webhook.invalid_signature provider={} eventType={}", gateway.code(), parsed.eventType());
            return Result.INVALID_SIGNATURE;
        }
        PaymentEventEntity stored = store(gateway.code(), parsed);
        if (stored == null) {
            log.info("webhook.duplicate provider={} eventId={}", gateway.code(), parsed.eventId());
            return Result.DUPLICATE;
        }
        try {
            for (GatewayEvent event : parsed.events()) {
                dispatch(gateway.code(), event);
            }
            markProcessed(stored.getId(), null);
            log.info("webhook.processed provider={} eventType={} eventId={}", gateway.code(), parsed.eventType(), parsed.eventId());
            return Result.PROCESSED;
        } catch (RuntimeException e) {
            markProcessed(stored.getId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    /** Returns the row to process, or null when this event was already processed. */
    private PaymentEventEntity store(String provider, WebhookParseResult parsed) {
        if (parsed.eventId() != null) {
            Optional<PaymentEventEntity> existing = eventRepository.findByProviderAndProviderEventId(provider, parsed.eventId());
            if (existing.isPresent()) {
                return existing.get().getProcessedAt() == null ? existing.get() : null;
            }
        }
        try {
            return tx.execute(s -> eventRepository.saveAndFlush(event(provider, parsed.eventId(), parsed, true)));
        } catch (DataIntegrityViolationException raced) {
            return null;
        }
    }

    private void dispatch(String provider, GatewayEvent event) {
        switch (event.type()) {
            case PAYMENT_CAPTURED, PAYMENT_AUTHORIZED, PAYMENT_FAILED -> paymentStateService.apply(provider, event.payment(), "webhook");
            case REFUND_PROCESSED -> paymentStateService.markRefundProcessed(provider, event.providerPaymentId(), event.refundId());
            case REFUND_FAILED -> paymentStateService.markRefundFailed(provider, event.providerPaymentId(), event.refundId());
        }
    }

    private void markProcessed(Long id, String error) {
        tx.executeWithoutResult(s -> eventRepository.findById(id).ifPresent(e -> {
            if (error == null) {
                e.setProcessedAt(clock.instant());
                e.setProcessingError(null);
            } else {
                e.setProcessingError(error.length() > 500 ? error.substring(0, 500) : error);
            }
        }));
    }

    private static PaymentEventEntity event(String provider, String eventId, WebhookParseResult parsed, boolean valid) {
        PaymentEventEntity e = new PaymentEventEntity();
        e.setProvider(provider);
        e.setProviderEventId(eventId);
        String type = parsed.eventType();
        e.setEventType(type == null ? null : type.length() > 50 ? type.substring(0, 50) : type);
        e.setPayload(parsed.payloadJson() == null ? "{}" : parsed.payloadJson());
        e.setSignatureValid(valid);
        return e;
    }
}
