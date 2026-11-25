package com.heuristiq.payment.gatewayaudit;

import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.payment.gatewayaudit.model.WebhookOutcome;
import com.heuristiq.payment.payment.model.PaymentWebhookEvent;
import com.heuristiq.payment.payment.repository.PaymentWebhookEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records webhook deliveries for the audit trail.
 * <ul>
 *   <li>{@link #publishProcessed}: for a delivery stored by the handler in its own dedupe-ledger row; only
 *       queues the audit event, joining the handler's transaction.</li>
 *   <li>{@link #recordUndelivered}: for DUPLICATE and REJECTED deliveries, which have no ledger row of
 *       their own; stores a row (without the provider event id, so the dedupe key stays free) and the audit
 *       event in a separate transaction, so it survives the handler throwing.</li>
 * </ul>
 * Neither method throws; failures are logged and counted ({@code payment.gateway.audit.record.failures}).
 */
@Slf4j
@Service
public class WebhookAuditRecorder {

    private final PaymentWebhookEventRepository events;
    private final AuditOutboxWriter outbox;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Counter recordFailures;

    public WebhookAuditRecorder(PaymentWebhookEventRepository events, AuditOutboxWriter outbox,
                                PlatformTransactionManager transactionManager, Clock clock, MeterRegistry meters) {
        this.events = events;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.recordFailures = meters.counter(GatewayAuditConstants.METRIC_RECORD_FAILURES);
    }

    /** Queues the audit event for a delivery already stored in the dedupe ledger as row {@code ledgerRowId}. */
    public void publishProcessed(String ledgerRowId, WebhookObservation o) {
        try {
            outbox.enqueue(event(ledgerRowId, o));
        } catch (RuntimeException e) {
            recordFailures.increment();
            log.warn("Could not queue webhook audit event (provider={}, outcome={}): {}", o.provider(), o.outcome(),
                    e.getClass().getSimpleName());
        }
    }

    /** Stores a ledger row and audit event for a DUPLICATE or REJECTED delivery. */
    public void recordUndelivered(WebhookObservation o) {
        try {
            transaction.executeWithoutResult(status -> {
                String id = UUID.randomUUID().toString();
                events.save(PaymentWebhookEvent.builder()
                        .id(id)
                        .providerEventId(null)
                        .claimedEventId(GatewayFailureSanitizer.truncate(o.providerEventId(), 255))
                        .provider(o.provider())
                        .signatureValid(o.signatureValid())
                        .outcome(o.outcome().name())
                        .paymentId(o.paymentId())
                        .eventType(GatewayFailureSanitizer.truncate(o.eventType(), GatewayAuditConstants.MAX_EVENT_TYPE))
                        .processedAt(LocalDateTime.now(clock))
                        .build());
                outbox.enqueue(event(id, o));
            });
        } catch (RuntimeException e) {
            recordFailures.increment();
            log.warn("Could not record webhook delivery (provider={}, outcome={}): {}", o.provider(), o.outcome(),
                    e.getClass().getSimpleName());
        }
    }

    private GatewayAuditEvent event(String id, WebhookObservation o) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(GatewayAuditConstants.D_GATEWAY, o.provider());
        details.put(GatewayAuditConstants.D_OPERATION, GatewayAuditConstants.OPERATION_WEBHOOK);
        details.put(GatewayAuditConstants.D_GATEWAY_REF,
                GatewayFailureSanitizer.clean(o.providerEventId(), GatewayAuditConstants.MAX_REFERENCE));
        details.put(GatewayAuditConstants.D_PAYMENT_ID, o.paymentId());
        details.put(GatewayAuditConstants.D_EVENT_TYPE,
                GatewayFailureSanitizer.clean(o.eventType(), GatewayAuditConstants.MAX_EVENT_TYPE));
        details.put(GatewayAuditConstants.D_WEBHOOK_OUTCOME, o.outcome().name());
        details.put(GatewayAuditConstants.D_SIGNATURE_VALID, o.signatureValid());
        if (o.outcome() == WebhookOutcome.REJECTED) {
            details.put(GatewayAuditConstants.D_FAILURE_REASON, GatewayAuditConstants.REASON_SIGNATURE_INVALID);
        }
        String outcome = o.outcome() == WebhookOutcome.REJECTED
                ? GatewayAuditConstants.AUDIT_FAILURE : GatewayAuditConstants.AUDIT_SUCCESS;
        return new GatewayAuditEvent(id, clock.instant(), outcome, null, null,
                MDC.get(CorrelationIdPropagation.MDC_KEY), details, o.tenantId());
    }
}
