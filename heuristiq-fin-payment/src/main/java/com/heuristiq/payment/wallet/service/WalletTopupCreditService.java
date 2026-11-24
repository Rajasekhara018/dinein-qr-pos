package com.heuristiq.payment.wallet.service;

import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.payment.config.WalletServiceProperties;
import com.heuristiq.payment.wallet.model.WalletTopupSession;
import com.heuristiq.payment.wallet.model.WalletTopupStatus;
import com.heuristiq.payment.wallet.port.WalletCreditClient;
import com.heuristiq.payment.wallet.port.WalletCreditCommand;
import com.heuristiq.payment.wallet.port.WalletCreditRejectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.heuristiq.payment.wallet.service.WalletConstants.*;

/**
 * Books the credit of a gateway-verified top-up in Rider_WalletService, the owner of every balance and ledger entry.
 *
 * <p>A verified top-up is first saved as PAID_AWAITING_CREDIT, so the paid money is on record before the wallet is
 * called. {@link #attemptCredit} then makes ONE idempotent call keyed by the top-up id: a repeat (duplicate webhook,
 * duplicate poll, the retry job, a crash between the call and the status update) returns the first journal and never
 * credits twice. A wallet refusal (for example 409 PURCHASE_DISABLED) or outage leaves the top-up awaiting credit with
 * a growing, capped backoff; {@link #retryDueCredits} (run by a ShedLock-guarded job) credits it later.
 * No database transaction is open during the wallet call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTopupCreditService {

    private final com.heuristiq.payment.wallet.repository.WalletTopupSessionRepository sessions;
    private final WalletCreditClient walletCreditClient;
    private final WalletServiceProperties properties;
    private final Clock clock;

    /**
     * Tries to credit the top-up once. Safe to call from any path and any number of times.
     *
     * @return true when the top-up is CAPTURED (credited) afterwards, false when it is still awaiting credit or is not a
     *         paid top-up at all
     */
    public boolean attemptCredit(String topupId) {
        Optional<WalletTopupSession> loaded = sessions.findById(topupId);
        if (loaded.isEmpty()) {
            return false;
        }
        WalletTopupSession session = loaded.get();
        if (session.getStatus() != WalletTopupStatus.PAID_AWAITING_CREDIT) {
            return session.getStatus() == WalletTopupStatus.CAPTURED;
        }
        boolean ownsCorrelation = MDC.get(CORRELATION_KEY) == null;
        if (ownsCorrelation) {
            MDC.put(CORRELATION_KEY, CORRELATION_PREFIX + session.getId());
        }
        try {
            String journalId = walletCreditClient.credit(new WalletCreditCommand(
                    session.getId(), session.getCustomerId(), session.getTenantId(),
                    Money.of(session.getAmount(), session.getCurrency()), session.getAppCode()));
            if (!StringUtils.hasText(journalId)) {
                recordFailure(session, REASON_MISSING_JOURNAL);
                return false;
            }
            return markCredited(session, journalId);
        } catch (WalletCreditRejectedException e) {
            recordFailure(session, e.errorCode() != null ? e.errorCode() : "HTTP_" + e.httpStatus());
        } catch (DownstreamUnavailableException e) {
            recordFailure(session, REASON_WALLET_UNAVAILABLE);
        } catch (RuntimeException e) {
            log.error("Unexpected error crediting wallet top-up {}", session.getId(), e);
            recordFailure(session, REASON_UNEXPECTED + e.getClass().getSimpleName());
        } finally {
            if (ownsCorrelation) {
                MDC.remove(CORRELATION_KEY);
            }
        }
        return false;
    }

    /**
     * True when the top-up is awaiting credit and its backoff has run out (or it was never attempted), so a status poll
     * by the customer may nudge the credit without hammering a wallet that is known to be refusing.
     */
    public boolean isCreditDue(WalletTopupSession session) {
        return session.getStatus() == WalletTopupStatus.PAID_AWAITING_CREDIT
                && (session.getCreditNextAttemptAt() == null
                || !session.getCreditNextAttemptAt().isAfter(LocalDateTime.now(clock)));
    }

    /**
     * One run of the retry job: credits at most {@code creditRetryBatchSize} due top-ups, oldest first. Never throws for
     * one bad top-up, so one stuck top-up cannot starve the others.
     *
     * @return how many top-ups were credited in this run
     */
    public int retryDueCredits() {
        List<WalletTopupSession> due = sessions.findCreditDue(LocalDateTime.now(clock), properties.getCreditRetryBatchSize());
        int credited = 0;
        for (WalletTopupSession session : due) {
            if (attemptCredit(session.getId())) {
                credited++;
            }
        }
        if (!due.isEmpty()) {
            log.info("Wallet top-up credit retry: {} due, {} credited", due.size(), credited);
        }
        return credited;
    }

    private boolean markCredited(WalletTopupSession session, String journalId) {
        LocalDateTime now = LocalDateTime.now(clock);
        session.setStatus(WalletTopupStatus.CAPTURED);
        session.setCreditedAt(now);
        session.setWalletJournalId(journalId);
        session.setCreditLastError(null);
        session.setCreditNextAttemptAt(null);
        session.setFailureReason(null);
        session.setUpdatedAt(now);
        try {
            sessions.save(session);
        } catch (OptimisticLockingFailureException concurrent) {
            // Another path (webhook, poll, job) updated the row at the same time. The wallet call was idempotent,
            // so the only question is what that other path stored.
            return sessions.findById(session.getId())
                    .map(s -> s.getStatus() == WalletTopupStatus.CAPTURED).orElse(false);
        }
        log.info("Wallet top-up credited: topupId={} customerId={} amount={} walletJournal={}",
                session.getId(), session.getCustomerId(), session.getAmount(), journalId);
        return true;
    }

    private void recordFailure(WalletTopupSession session, String reason) {
        int attempts = session.getCreditAttempts() + 1;
        session.setCreditAttempts(attempts);
        session.setCreditLastError(truncate(reason));
        session.setCreditNextAttemptAt(LocalDateTime.now(clock).plus(backoff(attempts)));
        session.setUpdatedAt(LocalDateTime.now(clock));
        try {
            sessions.save(session);
        } catch (OptimisticLockingFailureException concurrent) {
            log.info("Wallet top-up {} changed while its failed credit was recorded; leaving the newer state", session.getId());
            return;
        }
        if (attempts >= properties.getCreditAlertAfterAttempts()) {
            log.error("PAID top-up {} of customer {} ({}) is still not credited after {} attempts: {}. "
                            + "The money is safe at the gateway and on record here; check Rider_WalletService "
                            + "(rider.wallet.purchase-enabled) and the retry job.",
                    session.getId(), session.getCustomerId(), session.getAmount(), attempts, reason);
        } else {
            log.warn("Wallet credit of top-up {} failed (attempt {}): {} - will retry", session.getId(), attempts, reason);
        }
    }

    /** initial * 2^(attempts-1), capped. */
    private Duration backoff(int attempts) {
        long initial = properties.getCreditRetryInitialBackoffSeconds();
        long max = properties.getCreditRetryMaxBackoffSeconds();
        long seconds = initial << Math.min(Math.max(attempts - 1, 0), MAX_BACKOFF_DOUBLINGS);
        return Duration.ofSeconds(Math.min(max, seconds < 0 ? max : seconds));
    }

    private static String truncate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
