package com.heuristiq.paymentservice.payment.service;

import com.heuristiq.paymentservice.payment.gateway.GatewayClientConstants;
import com.heuristiq.paymentservice.config.PaymentPollingProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.paymentservice.payment.lifecycle.PaymentLifecycleContext;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentStateMachine;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import com.heuristiq.paymentservice.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static com.heuristiq.paymentservice.payment.PaymentConstants.LIFECYCLE_SOURCE_RECONCILE;
import static com.heuristiq.paymentservice.payment.PaymentConstants.LIFECYCLE_SOURCE_STATUS_POLL;
import static com.heuristiq.paymentservice.payment.PaymentConstants.POLLABLE_GATEWAYS;
import static com.heuristiq.paymentservice.payment.PaymentConstants.POLLABLE_STATUSES;
import static com.heuristiq.paymentservice.payment.PaymentConstants.POLL_KEYSET_START;

/**
 * Resolves the outcome of gateway payments that are still INITIATED/AUTHORIZED.
 *
 * <p>PAYU/TOUCAN/PINELABS have no webhook ingestion in this service, so without this poll a
 * checkout session created through one of them would sit in INITIATED forever, even if the
 * customer actually paid. RAZORPAY normally resolves through {@link PaymentWebhookHandler}, but a
 * webhook that never arrives (not configured in the Razorpay dashboard, wrong secret, server
 * unreachable) used to leave every Razorpay payment INITIATED for good. So RAZORPAY payments are
 * polled too, once they are older than {@code app.payment-polling.razorpay-grace-seconds} -- long
 * enough for the webhook to normally win. A payment that the webhook and this poll both try to
 * resolve is safe: the status is re-checked before each transition, and the entity's version column
 * makes the loser of a true race fail instead of applying the transition twice.
 *
 * <p>Per-candidate handling:
 * <ul>
 *   <li>gateway reports SUCCESS -> CAPTURED (whatever the payment's age -- an old payment that was
 *       in fact paid must not be expired), via {@link PaymentTransitionEffects#onPaymentCaptured}
 *       (receipt, driver earnings, Ride/NMS notifications).</li>
 *   <li>gateway reports FAILED -> FAILED, via {@link PaymentTransitionEffects#onPaymentFailed}.</li>
 *   <li>gateway reports PENDING and the payment is older than the max-age cutoff -> EXPIRED.</li>
 *   <li>gateway reports PENDING and still young, or the status check itself throws -> unchanged,
 *       tried again next run. A transient status-check failure is never treated as a payment failure.</li>
 * </ul>
 *
 * <p>How a scheduled run is shaped: candidates are read a page at a time (keyset on the payment id,
 * {@code app.payment-polling.page-size}), with no transaction open. Each candidate's gateway status
 * check runs outside any transaction, at most {@code app.payment-polling.concurrency} at once on
 * virtual threads. Only when there is something to apply does the payment get its own short
 * transaction, which re-reads the row and gives up (tried again next run) if its version or status
 * moved since the page was read; the entity's version column still fails a concurrent commit. So one
 * slow gateway no longer pins a pooled connection for the whole run, and one payment's rollback no
 * longer touches the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusPollingService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClientResolver gatewayClientResolver;
    private final PaymentPollingProperties paymentPollingProperties;
    private final PaymentTransitionEffects transitionEffects;
    private final GatewayAmountVerifier amountVerifier;
    private final TransactionTemplate transactionTemplate;

    private enum Outcome { CAPTURED, FAILED, EXPIRED, STILL_PENDING, ERRORED, SKIPPED, MISMATCH }

    /** One scheduled run: every candidate, page by page. Deliberately not transactional, see the class javadoc. */
    public void pollPendingGatewayPayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxAgeCutoff = now.minusHours(paymentPollingProperties.getMaxAgeHours());
        LocalDateTime razorpayGraceCutoff = now.minusSeconds(paymentPollingProperties.getRazorpayGraceSeconds());
        int pageSize = Math.max(1, paymentPollingProperties.getPageSize());
        int concurrency = Math.max(1, paymentPollingProperties.getConcurrency());
        AtomicIntegerArray counts = new AtomicIntegerArray(Outcome.values().length);
        int candidates = 0;
        // Elapsed time only (not business time), so a monotonic clock is the right one here.
        long deadline = System.nanoTime() + paymentPollingProperties.getRunBudget().toNanos();

        try (ExecutorService workers = Executors.newFixedThreadPool(concurrency,
                Thread.ofVirtual().name("payment-status-poll-", 0).factory())) {
            String after = POLL_KEYSET_START;
            while (!Thread.currentThread().isInterrupted()) {
                if (System.nanoTime() - deadline > 0) {
                    log.info("Payment status polling: run budget used up after {} candidate(s), the rest wait for the next run", candidates);
                    break;
                }
                List<Payment> page = paymentRepository.findPollCandidatesAfter(
                        POLLABLE_STATUSES, POLLABLE_GATEWAYS, after, pageSize);
                if (page.isEmpty()) {
                    break;
                }
                candidates += page.size();
                List<Future<?>> inFlight = new ArrayList<>(page.size());
                for (Payment payment : page) {
                    inFlight.add(workers.submit(() -> counts.incrementAndGet(
                            pollOne(payment, maxAgeCutoff, razorpayGraceCutoff).ordinal())));
                }
                awaitAll(inFlight);
                if (page.size() < pageSize) {
                    break;
                }
                after = page.get(page.size() - 1).getInternalPaymentId();
            }
        }

        log.info("Payment status polling: {} candidate(s) — captured={} failed={} expired={} stillPending={} errored={} amountMismatch={}",
                candidates, counts.get(Outcome.CAPTURED.ordinal()), counts.get(Outcome.FAILED.ordinal()),
                counts.get(Outcome.EXPIRED.ordinal()), counts.get(Outcome.STILL_PENDING.ordinal()),
                counts.get(Outcome.ERRORED.ordinal()), counts.get(Outcome.MISMATCH.ordinal()));
    }

    /** Waits for one page of checks before the next page is read, so at most one page is ever in memory. */
    private static void awaitAll(List<Future<?>> inFlight) {
        for (Future<?> future : inFlight) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                // pollOne catches its own failures; this is only a safety net so one task cannot end the run.
                log.warn("Status polling task failed: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            }
        }
    }

    /**
     * Polls one candidate: the gateway call first, with no transaction open, then (only if the
     * payment is still as it was read) the outcome in a transaction of its own.
     */
    private Outcome pollOne(Payment candidate, LocalDateTime maxAgeCutoff, LocalDateTime razorpayGraceCutoff) {
        if (!POLLABLE_STATUSES.contains(candidate.getStatus())) {
            return Outcome.SKIPPED;
        }
        // Give the Razorpay webhook first refusal on a payment that has only just been created.
        if (candidate.getGateway() == PaymentGatewayType.RAZORPAY
                && candidate.getCreatedAt() != null && candidate.getCreatedAt().isAfter(razorpayGraceCutoff)) {
            return Outcome.STILL_PENDING;
        }
        Optional<GatewayStatusResult> result = fetchStatus(candidate);
        if (result.isEmpty()) {
            return Outcome.ERRORED;
        }
        PaymentLifecycleContext.source(LIFECYCLE_SOURCE_STATUS_POLL);
        try {
            Outcome outcome = transactionTemplate.execute(status -> applyToCurrent(candidate, result.get(), maxAgeCutoff));
            return outcome == null ? Outcome.SKIPPED : outcome;
        } catch (RuntimeException e) {
            // One payment's failure (e.g. losing an optimistic-lock race to the webhook) must not
            // stop the rest of the batch.
            log.warn("Status polling could not resolve payment {}: {}", candidate.getInternalPaymentId(), e.getMessage());
            return Outcome.ERRORED;
        } finally {
            PaymentLifecycleContext.clear();
        }
    }

    /**
     * Inside the per-payment transaction: re-reads the payment and applies the gateway's answer only
     * if nothing changed it since the page was read (same version, still pollable). Anything else is
     * left for the next run, which asks the gateway again against the current state.
     *
     * <p>The re-read locks the row, so a webhook or reconcile writing the same payment either commits
     * first (and this sees the new version and skips) or waits until this commits. Without the lock the
     * version conflict only surfaced at commit, after the Ride and NMS notifications had already gone out.
     */
    private Outcome applyToCurrent(Payment candidate, GatewayStatusResult result, LocalDateTime maxAgeCutoff) {
        Optional<Payment> current = paymentRepository.findByIdForUpdate(candidate.getInternalPaymentId());
        if (current.isEmpty()
                || !Objects.equals(current.get().getVersion(), candidate.getVersion())
                || !POLLABLE_STATUSES.contains(current.get().getStatus())) {
            return Outcome.SKIPPED;
        }
        return apply(current.get(), result, maxAgeCutoff);
    }

    /**
     * Asks the gateway about one payment right now and applies the outcome. A payment that is
     * already past INITIATED/AUTHORIZED is returned unchanged. Used by the on-demand "check status"
     * calls (the customer's invoice page, the admin console).
     *
     * @throws NotFoundException if there is no such payment
     */
    @Transactional
    public Payment reconcile(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
        if (payment.getGateway() == null || !POLLABLE_STATUSES.contains(payment.getStatus())) {
            return payment;
        }
        LocalDateTime maxAgeCutoff = LocalDateTime.now().minusHours(paymentPollingProperties.getMaxAgeHours());
        PaymentLifecycleContext.source(LIFECYCLE_SOURCE_RECONCILE);
        try {
            fetchStatus(payment).ifPresent(result -> apply(payment, result, maxAgeCutoff));
        } finally {
            PaymentLifecycleContext.clear();
        }
        return payment;
    }

    /** The gateway's view of the payment, or empty when the check itself failed (never treated as a payment failure). */
    private Optional<GatewayStatusResult> fetchStatus(Payment payment) {
        try {
            StatusCheckGatewayClient client = gatewayClientResolver.resolveStatusCheck(payment.getGateway());
            return Optional.ofNullable(client.fetchStatus(payment));
        } catch (Exception e) {
            log.warn("Status-check failed for payment {} (gateway={}): {} — leaving unchanged, will retry next run",
                    payment.getInternalPaymentId(), payment.getGateway(), e.getMessage());
            return Optional.empty();
        }
    }

    private Outcome apply(Payment payment, GatewayStatusResult result, LocalDateTime maxAgeCutoff) {
        if (GatewayClientConstants.RESULT_SUCCESS.equals(result.status())) {
            if (!PaymentStateMachine.canTransition(payment.getStatus(), PaymentStatus.CAPTURED)) {
                return Outcome.SKIPPED;
            }
            String recordedPaymentId = payment.getProviderPaymentId();
            if (result.providerPaymentId() != null && recordedPaymentId != null && !recordedPaymentId.isBlank()
                    && !recordedPaymentId.equals(result.providerPaymentId())) {
                log.error("Status poll for payment {} reports provider payment id {} but {} is on record — ignoring",
                        payment.getInternalPaymentId(), result.providerPaymentId(), recordedPaymentId);
                return Outcome.SKIPPED;
            }
            Long reportedPaise = GatewayAmountVerifier.toPaiseOrNull(result.amount());
            if (!amountVerifier.matches(payment, reportedPaise, result.currency())) {
                return transitionEffects.onAmountMismatch(payment, reportedPaise, result.currency())
                        ? Outcome.MISMATCH : Outcome.SKIPPED;
            }
            if (result.providerPaymentId() != null && (recordedPaymentId == null || recordedPaymentId.isBlank())) {
                payment.setProviderPaymentId(result.providerPaymentId());
            }
            PaymentStateMachine.apply(payment, PaymentStatus.CAPTURED);
            paymentRepository.save(payment);
            transitionEffects.onPaymentCaptured(payment);
            return Outcome.CAPTURED;
        }
        if (GatewayClientConstants.RESULT_FAILED.equals(result.status())) {
            if (!transitionEffects.onPaymentFailed(payment, "Gateway reported failure status: " + result.rawProviderStatus())) {
                return Outcome.SKIPPED;
            }
            return Outcome.FAILED;
        }
        // PENDING: only now is it safe to give up on an old payment.
        if (payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(maxAgeCutoff)) {
            if (!transitionEffects.onPaymentExpired(payment)) {
                return Outcome.SKIPPED;
            }
            return Outcome.EXPIRED;
        }
        return Outcome.STILL_PENDING;
    }
}
