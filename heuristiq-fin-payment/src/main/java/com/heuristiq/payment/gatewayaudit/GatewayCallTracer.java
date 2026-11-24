package com.heuristiq.paymentservice.gatewayaudit;

import com.heuristiq.paymentservice.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.paymentservice.gatewayaudit.model.GatewayCallOutcome;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.resilient.CorrelationIdPropagation;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Times one gateway call, classifies the result and hands the observation to {@link GatewayCallRecorder}.
 * The call's own result or exception is always returned or rethrown unchanged; recording problems never
 * reach the caller. Only metadata is observed: the arguments and results of the call are not stored.
 */
@Component
public class GatewayCallTracer {

    private final GatewayCallRecorder recorder;

    public GatewayCallTracer(GatewayCallRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * @param paymentId    internal payment (or wallet top-up) id the call belongs to
     * @param reference    refund reference, idempotency key or order id identifying the request
     * @param fallbackRef  gateway identifier known before the call (reported when the call fails)
     * @param refOf        extracts the gateway's own identifier from a successful result
     */
    public <T> T trace(PaymentGatewayType gateway, GatewayCallOperation operation, String paymentId,
                       String reference, String fallbackRef, Function<T, String> refOf, Supplier<T> call) {
        long started = System.nanoTime();
        String correlationId = MDC.get(CorrelationIdPropagation.MDC_KEY);
        T result;
        try {
            result = call.get();
        } catch (RuntimeException | Error e) {
            safeRecord(new GatewayCallObservation(gateway.name(), operation, paymentId,
                    bound(reference, GatewayAuditConstants.MAX_REFERENCE), fallbackRef, httpStatus(e),
                    GatewayFailureSanitizer.truncate(e.getClass().getSimpleName(), GatewayAuditConstants.MAX_ERROR_CLASS),
                    isTimeout(e) ? GatewayCallOutcome.TIMEOUT : GatewayCallOutcome.FAILURE,
                    GatewayFailureSanitizer.reasonFor(e, GatewayAuditConstants.MAX_FAILURE_REASON),
                    elapsedMs(started), correlationId));
            throw e;
        }
        String ref = fallbackRef;
        try {
            String fromResult = result == null ? null : refOf.apply(result);
            if (fromResult != null && !fromResult.isBlank()) {
                ref = fromResult;
            }
        } catch (RuntimeException ignored) {
            // The result's shape never decides the payment; keep the reference known beforehand.
        }
        safeRecord(new GatewayCallObservation(gateway.name(), operation, paymentId,
                bound(reference, GatewayAuditConstants.MAX_REFERENCE), ref, null, null, GatewayCallOutcome.SUCCESS,
                null, elapsedMs(started), correlationId));
        return result;
    }

    private void safeRecord(GatewayCallObservation observation) {
        try {
            recorder.record(observation);
        } catch (RuntimeException ignored) {
            // The recorder already swallows and counts its own failures; this guards a replaced or mocked recorder.
        }
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static String bound(String value, int max) {
        return GatewayFailureSanitizer.truncate(value, max);
    }

    /** HTTP status of the gateway reply when the failure carries one anywhere in its cause chain. */
    static Integer httpStatus(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof WebClientResponseException w) {
                return w.getStatusCode().value();
            }
        }
        return null;
    }

    static boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getName();
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (name.endsWith("TimeoutException") || message.contains("timeout on blocking read")
                    || message.contains("timed out")) {
                return true;
            }
        }
        return false;
    }
}
