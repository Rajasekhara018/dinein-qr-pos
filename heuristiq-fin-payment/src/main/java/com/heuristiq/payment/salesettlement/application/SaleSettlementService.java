package com.heuristiq.payment.salesettlement.application;

import com.heuristiq.payment.salesettlement.config.SaleSettlementProperties;
import com.heuristiq.payment.salesettlement.domain.SaleEventIds;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementAction;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementConstants;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementOutbox;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementRequest;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementState;
import com.heuristiq.payment.salesettlement.domain.WalletSaleSettlementPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Settles or cancels the PENDING wallet sale of a gateway payment (docs/FRANCHISE_PLAN.md step 8, decision D7).
 *
 * <p><b>Recording.</b> When a gateway payment is captured (webhook, poll or reconcile) a SETTLE request is stored; when it
 * fails, expires or is cancelled a CANCEL request is stored. Both are written in the transaction that changes the payment, so
 * neither is lost. Cash payments are never recorded here: a cash sale settles inside the wallet when the driver's cash reaches
 * the company.
 *
 * <p><b>Sending</b> ({@link #sendDue}, a ShedLock job): oldest first, one request per sale at a time, keyed
 * {@code sale-settle:<eventId>} / {@code sale-cancel:<eventId>} so a repeat is harmless.
 * <ul>
 *   <li>SETTLE is sent only once its sale exists. Rides and matrimony book their gateway sale only after the payment is
 *   complete, so the capture (and its SETTLE request) normally comes first. Each run therefore first asks the wallet, in one
 *   lookup per organization and per {@link SaleSettlementConstants#MAX_LOOKUP_EVENTS} sales ({@code POST /internal/sales/lookups},
 *   a read), which of its due sales are booked. A sale not booked yet waits ({@code AWAITING_SALE}): it is looked up again after
 *   {@code await-sale-interval}, counts no attempt, and is given up (FAILED, {@code SALE_NEVER_BOOKED}) only when the request is
 *   older than {@code await-sale-max-age}. A booked sale is settled: done on success or 409 SALE_NOT_PENDING; a 404 then (the
 *   sale was reversed between the lookup and the call) waits the same way. A lookup the wallet cannot answer is a failed
 *   attempt, retried with the backoff below. So the wallet sees at most one cheap read per organization per run, never a storm
 *   of 404 settlements.</li>
 *   <li>CANCEL: first checks that the sale was not paid after all (a gateway accepts a retry on the same order after a failure,
 *   and FAILED/EXPIRED may still become CAPTURED); if it was, the request is SUPERSEDED. Done on success, 409 SALE_NOT_PENDING
 *   or 404 (nothing was booked: the usual case, as a sale is booked only after payment).</li>
 *   <li>400 (the wallet refuses the request as such) or a payment without an organization: FAILED at once. Anything else is
 *   retried with a doubling, capped backoff, and given up after {@code max-attempts}; the wallet's STALE_SETTLEMENT alert keeps
 *   reporting a sale that stays PENDING.</li>
 * </ul>
 * No database transaction is open during the wallet call.
 */
@Service
public class SaleSettlementService {

    private static final Logger log = LoggerFactory.getLogger(SaleSettlementService.class);

    private final SaleSettlementOutbox outbox;
    private final WalletSaleSettlementPort wallet;
    private final SaleSettlementProperties properties;
    private final Clock clock;

    public SaleSettlementService(SaleSettlementOutbox outbox, WalletSaleSettlementPort wallet, SaleSettlementProperties properties,
                                 Clock clock) {
        this.outbox = outbox;
        this.wallet = wallet;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Records that a gateway payment was captured: its sale is to be settled. Ignored for a payment that pays for no known sale.
     *
     * @return true when a request was stored
     */
    public boolean paymentCaptured(String paymentId, String tenantId, String sourceService, String sourceReference, String rideId) {
        return record(SaleSettlementAction.SETTLE, paymentId, tenantId, sourceService, sourceReference, rideId, null);
    }

    /**
     * Records that a gateway payment failed, expired or was cancelled: its sale (if one was booked) is to be cancelled.
     *
     * @return true when a request was stored
     */
    public boolean paymentAbandoned(String paymentId, String tenantId, String sourceService, String sourceReference, String rideId,
                                    String reason) {
        return record(SaleSettlementAction.CANCEL, paymentId, tenantId, sourceService, sourceReference, rideId, reason);
    }

    private boolean record(SaleSettlementAction action, String paymentId, String tenantId, String sourceService,
                           String sourceReference, String rideId, String reason) {
        if (!properties.enabled()) {
            return false;
        }
        Optional<String> eventId = SaleEventIds.of(sourceService, sourceReference, rideId);
        if (eventId.isEmpty()) {
            log.debug("Payment {} pays for no wallet sale (sourceService={}); nothing to settle", paymentId, sourceService);
            return false;
        }
        boolean stored = outbox.enqueue(eventId.get(), action, SaleEventIds.idempotencyKey(action, eventId.get()), paymentId,
                tenantId, truncate(reason));
        if (stored) {
            log.info("Sale {} of payment {} queued for {}", eventId.get(), paymentId, action);
        }
        return stored;
    }

    /**
     * One run of the job: sends at most {@code batch-size} due requests. Never throws for one request.
     *
     * @return how many requests finished (DONE or SUPERSEDED) in this run
     */
    public int sendDue() {
        if (!properties.enabled()) {
            return 0;
        }
        List<SaleSettlementRequest> due = outbox.due(clock.instant(), properties.batchSize());
        Map<Long, Boolean> booked = bookedSettlements(due);
        int finished = 0;
        for (SaleSettlementRequest request : due) {
            if (send(request, booked.get(request.id()))) {
                finished++;
            }
        }
        if (!due.isEmpty()) {
            log.info("Sale settlement run: {} due, {} finished", due.size(), finished);
        }
        return finished;
    }

    /**
     * Which due SETTLE requests have their sale booked: one wallet lookup per organization and per
     * {@link SaleSettlementConstants#MAX_LOOKUP_EVENTS} sales. A request whose lookup could not be answered is absent.
     */
    private Map<Long, Boolean> bookedSettlements(List<SaleSettlementRequest> due) {
        Map<String, List<SaleSettlementRequest>> byTenant = new LinkedHashMap<>();
        for (SaleSettlementRequest request : due) {
            if (request.action() == SaleSettlementAction.SETTLE && request.tenantId() != null && !request.tenantId().isBlank()) {
                byTenant.computeIfAbsent(request.tenantId(), t -> new ArrayList<>()).add(request);
            }
        }
        Map<Long, Boolean> booked = new HashMap<>();
        byTenant.forEach((tenantId, requests) -> {
            for (int from = 0; from < requests.size(); from += SaleSettlementConstants.MAX_LOOKUP_EVENTS) {
                List<SaleSettlementRequest> chunk = requests.subList(from,
                        Math.min(requests.size(), from + SaleSettlementConstants.MAX_LOOKUP_EVENTS));
                Optional<Set<String>> answer = lookUp(tenantId, chunk);
                answer.ifPresent(sales -> chunk.forEach(r -> booked.put(r.id(), sales.contains(r.eventId()))));
            }
        });
        return booked;
    }

    private Optional<Set<String>> lookUp(String tenantId, List<SaleSettlementRequest> chunk) {
        try {
            return wallet.bookedSales(tenantId, chunk.stream().map(SaleSettlementRequest::eventId).distinct().toList());
        } catch (RuntimeException e) {
            log.error("Unexpected error looking up {} sale(s) in the wallet; they are retried", chunk.size(), e);
            return Optional.empty();
        }
    }

    /** @param booked whether a SETTLE request's sale is booked in the wallet; null when the lookup could not tell */
    private boolean send(SaleSettlementRequest request, Boolean booked) {
        Instant now = clock.instant();
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            log.error("Payment {} has no organization; the {} of sale {} can not be sent", request.paymentId(), request.action(),
                    request.eventId());
            outbox.finish(request.id(), SaleSettlementState.FAILED, null, null, SaleSettlementConstants.ERROR_TENANT_MISSING, now);
            return false;
        }
        if (request.action() == SaleSettlementAction.CANCEL && outbox.salePaidAfterAll(request.paymentId())) {
            log.info("Sale {} was paid after all; its cancellation is not sent", request.eventId());
            outbox.finish(request.id(), SaleSettlementState.SUPERSEDED, SaleSettlementConstants.OUTCOME_PAID_AFTER_ALL, null, null, now);
            return true;
        }
        if (request.action() == SaleSettlementAction.SETTLE) {
            if (booked == null) {
                return retry(request, SaleSettlementConstants.ERROR_LOOKUP_UNAVAILABLE);
            }
            if (!booked) {
                return awaitSale(request);
            }
        }
        boolean ownsCorrelation = MDC.get(SaleSettlementConstants.CORRELATION_KEY) == null;
        if (ownsCorrelation) {
            MDC.put(SaleSettlementConstants.CORRELATION_KEY, SaleSettlementConstants.CORRELATION_PREFIX + request.id());
        }
        try {
            return apply(request, wallet.send(request));
        } catch (RuntimeException e) {
            log.error("Unexpected error sending the {} of sale {}", request.action(), request.eventId(), e);
            return retry(request, SaleSettlementConstants.ERROR_UNEXPECTED + e.getClass().getSimpleName());
        } finally {
            if (ownsCorrelation) {
                MDC.remove(SaleSettlementConstants.CORRELATION_KEY);
            }
        }
    }

    private boolean apply(SaleSettlementRequest request, WalletSaleSettlementPort.Answer answer) {
        Instant now = clock.instant();
        boolean settle = request.action() == SaleSettlementAction.SETTLE;
        switch (answer.result()) {
            case DONE -> {
                outbox.finish(request.id(), SaleSettlementState.DONE, settle ? SaleSettlementConstants.OUTCOME_SETTLED
                        : SaleSettlementConstants.OUTCOME_CANCELLED, answer.journalId(), null, now);
                return true;
            }
            case NOT_PENDING -> {
                if (!settle) {
                    log.warn("Sale {} was already settled or cancelled; its cancellation had nothing to do", request.eventId());
                }
                outbox.finish(request.id(), SaleSettlementState.DONE, SaleSettlementConstants.OUTCOME_ALREADY_DONE, null, null, now);
                return true;
            }
            case SALE_NOT_FOUND -> {
                if (!settle) {
                    outbox.finish(request.id(), SaleSettlementState.DONE, SaleSettlementConstants.OUTCOME_NO_SALE, null, null, now);
                    return true;
                }
                return awaitSale(request); // reversed between the lookup and the call: wait as for a sale not booked
            }
            case REJECTED -> {
                log.error("Wallet refused the {} of sale {} (payment {}): {}", request.action(), request.eventId(),
                        request.paymentId(), answer.detail());
                outbox.finish(request.id(), SaleSettlementState.FAILED, null, null, truncate(answer.detail()), now);
                return false;
            }
            default -> {
                return retry(request, answer.detail() == null ? SaleSettlementConstants.ERROR_WALLET_UNAVAILABLE : answer.detail());
            }
        }
    }

    /**
     * A SETTLE whose sale is not booked yet: looked at again after {@code await-sale-interval}, with no attempt counted, and
     * given up only once the request is older than {@code await-sale-max-age}.
     */
    private boolean awaitSale(SaleSettlementRequest request) {
        Instant now = clock.instant();
        if (request.createdAt() != null && !now.isBefore(request.createdAt().plus(properties.awaitSaleMaxAge()))) {
            log.error("Sale {} of payment {} was never booked in the wallet within {}; its settlement is given up", request.eventId(),
                    request.paymentId(), properties.awaitSaleMaxAge());
            outbox.finish(request.id(), SaleSettlementState.FAILED, null, null, SaleSettlementConstants.ERROR_SALE_NEVER_BOOKED, now);
            return false;
        }
        outbox.awaitSale(request.id(), SaleSettlementConstants.ERROR_AWAITING_SALE, now.plus(properties.awaitSaleInterval()));
        log.debug("Sale {} is not booked in the wallet yet; its settlement waits", request.eventId());
        return false;
    }

    private boolean retry(SaleSettlementRequest request, String error) {
        int attempts = request.attempts() + 1;
        Instant now = clock.instant();
        if (attempts >= properties.maxAttempts()) {
            log.error("Gave up the {} of sale {} (payment {}) after {} attempts, last error {}; the wallet's STALE_SETTLEMENT alert "
                    + "reports the sale while it stays PENDING", request.action(), request.eventId(), request.paymentId(), attempts, error);
            outbox.finish(request.id(), SaleSettlementState.FAILED, null, null,
                    truncate(SaleSettlementConstants.ERROR_GAVE_UP + " " + error), now);
            return false;
        }
        outbox.retryLater(request.id(), attempts, truncate(error), now.plus(backoff(attempts)));
        log.warn("{} of sale {} not done (attempt {}): {} - will retry", request.action(), request.eventId(), attempts, error);
        return false;
    }

    /** initial x 2^(attempts-1), capped at max. */
    Duration backoff(int attempts) {
        int doublings = Math.min(Math.max(attempts - 1, 0), SaleSettlementConstants.MAX_BACKOFF_DOUBLINGS);
        Duration delay = properties.initialBackoff().multipliedBy(1L << doublings);
        return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
    }

    private static String truncate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() <= SaleSettlementConstants.MAX_TEXT ? text : text.substring(0, SaleSettlementConstants.MAX_TEXT);
    }
}
