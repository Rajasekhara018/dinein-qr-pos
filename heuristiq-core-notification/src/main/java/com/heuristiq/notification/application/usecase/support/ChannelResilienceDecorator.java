package com.heuristiq.notification.application.usecase.support;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.heuristiq.notification.application.dto.ChannelSendOutcome;
import com.heuristiq.notification.application.dto.ChannelSendOutcome.AttemptResult;
import com.heuristiq.notification.application.port.out.ProviderSendResult;
import com.heuristiq.notification.domain.model.NotificationChannel;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Wraps a per-channel {@link com.heuristiq.notification.application.port.out.ChannelProvider#send} call with
 * Resilience4j Bulkhead + CircuitBreaker + Retry, replacing the hand-rolled
 * {@code ConcurrentHashMap} circuit breaker and raw {@code Semaphore} rate limiter that used to
 * live inline in {@code NotificationDispatchService}. {@code send} returning {@code false} is
 * treated the same as a thrown exception -- both retry-eligible, both count as a circuit-breaker
 * failure -- since {@code ChannelProvider}'s boolean contract has no room for a "retry-safe" vs
 * "not" distinction; this mirrors the original {@code sendWithRetry}'s behavior exactly.
 *
 * <p>Every provider call is also measured and reported in {@link ChannelSendOutcome#attemptResults()},
 * so each retry can be stored as its own attempt record.
 */
@Component
public class ChannelResilienceDecorator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final Clock clock;

    @Autowired
    public ChannelResilienceDecorator(CircuitBreakerRegistry circuitBreakerRegistry,
                                       RetryRegistry retryRegistry,
                                       BulkheadRegistry bulkheadRegistry,
                                       Clock clock) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.clock = clock;
    }

    public ChannelResilienceDecorator(CircuitBreakerRegistry circuitBreakerRegistry,
                                       RetryRegistry retryRegistry,
                                       BulkheadRegistry bulkheadRegistry) {
        this(circuitBreakerRegistry, retryRegistry, bulkheadRegistry, Clock.systemDefaultZone());
    }

    /** Boolean form kept for callers (and tests) that do not need provider detail. */
    public ChannelSendOutcome call(NotificationChannel channel, Supplier<Boolean> sendCall) {
        return callDetailed(channel, () -> Boolean.TRUE.equals(sendCall.get())
                ? ProviderSendResult.ok(null, null, null)
                : ProviderSendResult.failed(null, ProviderSendResult.CODE_REJECTED, null));
    }

    /** Runs the provider call under the channel's bulkhead, circuit breaker and retry, recording every call. */
    public ChannelSendOutcome callDetailed(NotificationChannel channel, Supplier<ProviderSendResult> sendCall) {
        String name = name(channel);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(name);
        Retry retry = retryRegistry.retry(name);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);

        AtomicInteger attempts = new AtomicInteger(0);
        List<AttemptResult> results = new ArrayList<>();
        Supplier<Boolean> countedFailFastOnFalse = () -> {
            int number = attempts.incrementAndGet();
            LocalDateTime startedAt = LocalDateTime.now(clock);
            long startNanos = System.nanoTime();
            ProviderSendResult result;
            try {
                result = sendCall.get();
                if (result == null) {
                    result = ProviderSendResult.failed(null, ProviderSendResult.CODE_REJECTED, null);
                }
            } catch (RuntimeException ex) {
                results.add(toAttempt(number, startedAt, startNanos, ProviderSendResult.failed(null,
                        ProviderSendResult.CODE_EXCEPTION, ErrorSanitizer.sanitize(ex.getMessage()))));
                throw ex;
            }
            results.add(toAttempt(number, startedAt, startNanos, result));
            if (result.success()) {
                return true;
            }
            throw new ChannelSendFailedException(ErrorSanitizer.sanitize(result.errorMessage()) == null
                    ? ProviderSendResult.DEFAULT_FAILURE_MESSAGE
                    : ErrorSanitizer.sanitize(result.errorMessage()));
        };

        Supplier<Boolean> decorated = Bulkhead.decorateSupplier(bulkhead,
                Retry.decorateSupplier(retry,
                        CircuitBreaker.decorateSupplier(circuitBreaker, countedFailFastOnFalse)));

        try {
            boolean success = decorated.get();
            return new ChannelSendOutcome(success, null, attempts.get(), results);
        } catch (CallNotPermittedException ex) {
            return refused(attempts, results, "Provider circuit open", "CIRCUIT_OPEN");
        } catch (BulkheadFullException ex) {
            return refused(attempts, results, "Provider at capacity", "BULKHEAD_FULL");
        } catch (ChannelSendFailedException ex) {
            return new ChannelSendOutcome(false, ex.getMessage(), attempts.get(), results);
        } catch (RuntimeException ex) {
            return new ChannelSendOutcome(false, ErrorSanitizer.sanitize(ex.getMessage()), attempts.get(), results);
        }
    }

    /** A call refused before reaching the provider still gets an attempt record, so the gap is visible. */
    private ChannelSendOutcome refused(AtomicInteger attempts, List<AttemptResult> results, String message, String code) {
        results.add(new AttemptResult(attempts.get() + 1, false, null, null, null, code, message,
                LocalDateTime.now(clock), 0L));
        return new ChannelSendOutcome(false, message, attempts.get(), results);
    }

    private AttemptResult toAttempt(int number, LocalDateTime startedAt, long startNanos, ProviderSendResult r) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        return new AttemptResult(number, r.success(), r.providerName(), r.providerMessageId(), r.providerStatus(),
                r.errorCode(), ErrorSanitizer.sanitize(r.errorMessage()), startedAt, latencyMs);
    }

    private String name(NotificationChannel channel) {
        return channel.name().toLowerCase();
    }

    private static final class ChannelSendFailedException extends RuntimeException {
        ChannelSendFailedException(String message) {
            super(message);
        }
    }
}
