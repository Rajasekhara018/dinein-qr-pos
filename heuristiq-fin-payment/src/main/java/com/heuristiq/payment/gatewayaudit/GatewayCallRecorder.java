package com.heuristiq.payment.gatewayaudit;

import com.heuristiq.payment.security.TenantAccess;
import com.heuristiq.payment.gatewayaudit.model.GatewayCallOutcome;
import com.heuristiq.payment.gatewayaudit.model.PaymentGatewayCall;
import com.heuristiq.payment.gatewayaudit.repository.PaymentGatewayCallRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
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
 * Persists the gateway call log row and its audit outbox row in one separate transaction
 * ({@code REQUIRES_NEW}), so a failed call is still logged when the caller's transaction rolls back.
 * Logging must never break a payment: every failure is caught, logged and counted
 * ({@code payment.gateway.audit.record.failures}).
 */
@Slf4j
@Service
public class GatewayCallRecorder {

    private final PaymentGatewayCallRepository calls;
    private final AuditOutboxWriter outbox;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Counter recordFailures;

    public GatewayCallRecorder(PaymentGatewayCallRepository calls, AuditOutboxWriter outbox,
                               PlatformTransactionManager transactionManager, Clock clock, MeterRegistry meters) {
        this.calls = calls;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.recordFailures = meters.counter(GatewayAuditConstants.METRIC_RECORD_FAILURES);
    }

    /** Records one call. Never throws. */
    public void record(GatewayCallObservation o) {
        try {
            transaction.executeWithoutResult(status -> store(o));
        } catch (RuntimeException e) {
            recordFailures.increment();
            log.warn("Could not record gateway call log (gateway={}, operation={}, payment={}): {}",
                    o.gateway(), o.operation(), o.paymentId(), e.getClass().getSimpleName());
        }
    }

    private void store(GatewayCallObservation o) {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        int attempt = o.paymentId() == null ? 1
                : (int) Math.min(Integer.MAX_VALUE - 1L, calls.countByPaymentIdAndOperation(o.paymentId(), o.operation()) + 1);
        calls.save(PaymentGatewayCall.builder()
                .id(id)
                .gateway(o.gateway())
                .operation(o.operation())
                .paymentId(o.paymentId())
                .reference(o.reference())
                .gatewayRef(o.gatewayRef())
                .httpStatus(o.httpStatus())
                .errorClass(o.errorClass())
                .outcome(o.outcome())
                .failureReason(o.failureReason())
                .latencyMs(o.latencyMs())
                .attempt(attempt)
                .correlationId(o.correlationId())
                .createdAt(now)
                .build());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put(GatewayAuditConstants.D_GATEWAY, o.gateway());
        details.put(GatewayAuditConstants.D_OPERATION, o.operation().name());
        details.put(GatewayAuditConstants.D_GATEWAY_REF, o.gatewayRef());
        details.put(GatewayAuditConstants.D_PAYMENT_ID, o.paymentId());
        details.put(GatewayAuditConstants.D_REFERENCE, o.reference());
        details.put(GatewayAuditConstants.D_ATTEMPT, attempt);
        details.put(GatewayAuditConstants.D_CALL_OUTCOME, o.outcome().name());
        details.put(GatewayAuditConstants.D_ERROR_CLASS, o.errorClass());
        details.put(GatewayAuditConstants.D_FAILURE_REASON, o.failureReason());
        String auditOutcome = o.outcome() == GatewayCallOutcome.SUCCESS
                ? GatewayAuditConstants.AUDIT_SUCCESS : GatewayAuditConstants.AUDIT_FAILURE;
        // The call is made on behalf of the current caller (a user, or a service acting for an organization).
        outbox.enqueue(new GatewayAuditEvent(id, clock.instant(), auditOutcome, o.httpStatus(), o.latencyMs(),
                o.correlationId(), details, TenantAccess.stamp()));
    }
}
