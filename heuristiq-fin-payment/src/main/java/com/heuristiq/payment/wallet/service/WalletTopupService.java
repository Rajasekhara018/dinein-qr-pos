package com.heuristiq.paymentservice.wallet.service;

import com.heuristiq.paymentservice.shared.money.MoneyRequests;
import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.payment.gateway.GatewayClientConstants;
import com.heuristiq.paymentservice.security.TenantAccess;
import com.heuristiq.paymentservice.config.PaymentGatewayRoutingProperties;
import com.heuristiq.paymentservice.config.PaymentPollingProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.gatewayaudit.GatewayAuditConstants;
import com.heuristiq.paymentservice.gatewayaudit.WebhookAuditRecorder;
import com.heuristiq.paymentservice.gatewayaudit.WebhookObservation;
import com.heuristiq.paymentservice.gatewayaudit.model.WebhookOutcome;
import com.heuristiq.paymentservice.payment.gateway.razorpay.RazorpayWebhookSignatureVerifier;
import com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentWebhookEvent;
import com.heuristiq.paymentservice.payment.service.GatewayAmountVerifier;
import com.heuristiq.paymentservice.payment.repository.PaymentWebhookEventRepository;
import com.heuristiq.paymentservice.shared.exception.ConflictException;
import com.heuristiq.paymentservice.shared.exception.NotFoundException;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import com.heuristiq.paymentservice.wallet.dto.CreateWalletTopupRequest;
import com.heuristiq.paymentservice.wallet.dto.WalletTopupResponse;
import com.heuristiq.paymentservice.wallet.model.WalletTopupSession;
import com.heuristiq.paymentservice.wallet.model.WalletTopupStatus;
import com.heuristiq.paymentservice.wallet.repository.WalletTopupSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A real, gateway-verified wallet top-up flow — replaces WalletController's old
 * {@code POST /{customerId}/topup}, which trusted the caller's amount and credited the wallet
 * with NO payment verification at all (anyone could top up for free). This mirrors
 * PaymentService's own INITIATED -> CAPTURED state machine and reuses its exact gateway-adapter
 * infrastructure (PaymentGatewayClientResolver + the 4 CreateSessionGatewayClient/
 * StatusCheckGatewayClient implementations), just against a separate {@link WalletTopupSession}
 * record instead of {@code payments} — see that entity's javadoc for why it's kept separate
 * rather than reusing the ride-scoped {@code payments} table.
 *
 * The gateway adapters are typed to {@link Payment}, not this service's own entity — a
 * short-lived, never-persisted {@link Payment} is built purely as the data-carrier those ports
 * expect (see {@link #transientPaymentFor}); only amount/currency/customerId/internalPaymentId/
 * providerOrderId/providerPaymentId/gateway are ever read by any adapter (verified against all 8
 * adapter implementations), so a synthetic rideId there is never actually used for anything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTopupService {

    private final WalletTopupSessionRepository walletTopupSessionRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final WalletTopupCreditService creditService;
    private final PaymentGatewayClientResolver gatewayClientResolver;
    private final PaymentGatewayRoutingProperties gatewayRoutingProperties;
    private final PaymentPollingProperties paymentPollingProperties;
    private final RazorpayWebhookSignatureVerifier webhookSignatureVerifier;
    private final GatewayAmountVerifier amountVerifier;
    private final WebhookAuditRecorder webhookAudit;

    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.paymentservice.config.WalletServiceProperties walletProperties =
            new com.heuristiq.paymentservice.config.WalletServiceProperties();

    /**
     * Starts a top-up checkout. Not transactional on purpose: the INITIATED session is persisted first,
     * the gateway call runs with no database transaction open, and the order id is stored afterwards. A
     * gateway failure marks the session FAILED instead of leaving an order without a row.
     */
    public WalletTopupResponse createTopupSession(CreateWalletTopupRequest req) {
        if (TenantAccess.stamp() == null) {
            throw new ConflictException("An organization is required before starting wallet funding");
        }
        String originApp = req.appCode() == null ? walletProperties.getAppCode() : req.appCode();
        Optional<WalletTopupSession> existing = walletTopupSessionRepository.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            return replayTopup(req, originApp, existing.get());
        }

        PaymentGatewayType gatewayType = (req.gateway() == null || req.gateway().isBlank())
                ? PaymentGatewayType.fromValue(gatewayRoutingProperties.getDefaultGateway())
                : PaymentGatewayType.fromValue(req.gateway());

        WalletTopupSession session = WalletTopupSession.builder()
                .id(UUID.randomUUID().toString())
                .customerId(req.customerId())
                .tenantId(TenantAccess.stamp())
                .appCode(originApp)
                .amount(MoneyRequests.valueIn(req.amount(), amountVerifier.currency()))
                .currency(amountVerifier.currency())
                .gateway(gatewayType)
                .status(WalletTopupStatus.INITIATED)
                .idempotencyKey(req.idempotencyKey())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        WalletTopupSession persisted;
        try {
            persisted = walletTopupSessionRepository.save(session);
        } catch (DataIntegrityViolationException e) {
            // Apply the same ownership and request checks to a concurrent winner as to an ordinary replay.
            return replayTopup(req, originApp, walletTopupSessionRepository.findByIdempotencyKey(req.idempotencyKey()).orElseThrow(() -> e));
        }

        GatewaySessionResult result;
        try {
            CreateSessionGatewayClient client = gatewayClientResolver.resolveCreateSession(gatewayType);
            result = client.createSession(transientPaymentFor(persisted));
        } catch (RuntimeException e) {
            log.error("Checkout-session creation failed for wallet topup {} ({}): {}", persisted.getId(), gatewayType, e.getMessage());
            persisted.setStatus(WalletTopupStatus.FAILED);
            persisted.setFailureReason("Gateway session creation failed");
            persisted.setUpdatedAt(LocalDateTime.now());
            walletTopupSessionRepository.save(persisted);
            throw e;
        }

        persisted.setProviderOrderId(result.providerOrderId());
        persisted.setUpdatedAt(LocalDateTime.now());
        WalletTopupSession saved = walletTopupSessionRepository.save(persisted);
        log.info("Created {} wallet-topup checkout session (providerOrderId={}) for topup {}",
                gatewayType, result.providerOrderId(), saved.getId());

        return WalletTopupResponse.from(saved, result);
    }

    /**
     * Returns the top-up. While a RAZORPAY top-up is still INITIATED the gateway is asked for its
     * outcome first, so the customer app's status poll sees the credit as soon as Razorpay reports
     * the order paid instead of waiting for the webhook or the next scheduled poll. A gateway error
     * never fails the read: the stored state is returned and the webhook or scheduler settles it later.
     */
    public WalletTopupResponse getTopup(String topupId) {
        WalletTopupSession session = walletTopupSessionRepository.findById(topupId)
                .filter(found -> TenantAccess.canSee(found.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Wallet top-up not found: " + topupId));
        if (session.getStatus() == WalletTopupStatus.INITIATED && session.getGateway() == PaymentGatewayType.RAZORPAY) {
            refreshFromGateway(session);
        } else if (creditService.isCreditDue(session)) {
            creditService.attemptCredit(session.getId());
        }
        // Re-read: the credit may have just completed (or another path changed the row).
        return WalletTopupResponse.from(walletTopupSessionRepository.findById(topupId).orElse(session));
    }

    private void refreshFromGateway(WalletTopupSession session) {
        try {
            GatewayStatusResult result = gatewayClientResolver.resolveStatusCheck(session.getGateway())
                    .fetchStatus(transientPaymentFor(session));
            if (GatewayClientConstants.RESULT_SUCCESS.equals(result.status())) {
                captureIfVerified(session, result.providerPaymentId(),
                        GatewayAmountVerifier.toPaiseOrNull(result.amount()), result.currency());
            } else if (GatewayClientConstants.RESULT_FAILED.equals(result.status())) {
                failIfNotAlready(session, "Gateway reported failure status: " + result.rawProviderStatus());
            }
        } catch (Exception e) {
            log.warn("Live status check failed for wallet topup {}: {} — returning stored state", session.getId(), e.getMessage());
        }
    }

    /**
     * Razorpay webhook for wallet top-ups — separate endpoint from PaymentService's ride-payment
     * webhook (different table to look the order up in), same signature verification and the same
     * provider-event-id dedupe ledger (payment_webhook_events; providerEventId is unique
     * regardless of which flow the event belongs to, so sharing the table is safe).
     */
    public void handleTopupWebhook(String rawBody, String signature, String providerEventIdHeader) {
        String eventId = (providerEventIdHeader != null && !providerEventIdHeader.isBlank())
                ? providerEventIdHeader
                : webhookSignatureVerifier.sha256Hex(rawBody);

        if (!webhookSignatureVerifier.verify(rawBody, signature)) {
            webhookAudit.recordUndelivered(new WebhookObservation(GatewayAuditConstants.PROVIDER_RAZORPAY, null, eventId,
                    false, WebhookOutcome.REJECTED, null));
            throw new PaymentServiceException("Webhook signature verification failed");
        }

        Optional<PaymentWebhookEvent> alreadyProcessed = webhookEventRepository.findByProviderEventId(eventId);
        if (alreadyProcessed.isPresent()) {
            log.info("Wallet-topup webhook event {} already processed — ignoring duplicate delivery", eventId);
            PaymentWebhookEvent first = alreadyProcessed.get();
            webhookAudit.recordUndelivered(new WebhookObservation(GatewayAuditConstants.PROVIDER_RAZORPAY,
                    first.getEventType(), eventId, true, WebhookOutcome.DUPLICATE, first.getPaymentId()));
            return;
        }

        JSONObject payload = new JSONObject(rawBody);
        String eventType = payload.optString("event", "unknown");
        JSONObject paymentEntity = webhookSignatureVerifier.extractPaymentEntity(payload);
        if (paymentEntity == null) {
            recordWebhookEvent(eventId, null, eventType, WebhookOutcome.IGNORED);
            return;
        }

        String providerOrderId = paymentEntity.optString("order_id", null);
        String providerPaymentId = paymentEntity.optString("id", null);
        Optional<WalletTopupSession> sessionOpt = providerOrderId != null
                ? walletTopupSessionRepository.findByProviderOrderId(providerOrderId)
                : Optional.empty();

        if (sessionOpt.isEmpty()) {
            log.warn("Wallet-topup webhook event {} referenced unknown order {} — recording and ignoring", eventId, providerOrderId);
            recordWebhookEvent(eventId, null, eventType, WebhookOutcome.IGNORED);
            return;
        }

        WalletTopupSession session = sessionOpt.get();

        if ("payment.captured".equals(eventType) || "order.paid".equals(eventType)) {
            captureIfVerified(session, providerPaymentId, reportedPaise(paymentEntity), paymentEntity.optString("currency", null));
        } else if ("payment.failed".equals(eventType)) {
            if (!conflictsWithRecordedPaymentId(session, providerPaymentId)) {
                failIfNotAlready(session, paymentEntity.optString("error_description", "Payment failed"));
            }
        } else {
            log.info("Wallet-topup webhook event {} of type {} does not drive a state transition — recorded only", eventId, eventType);
        }

        recordWebhookEvent(eventId, session.getId(), eventType, WebhookOutcome.PROCESSED);
    }

    /**
     * Resolves the outcome of PAYU/TOUCAN/PINELABS wallet top-ups — those gateways have no
     * webhook ingestion here — and of RAZORPAY ones once they are older than the Razorpay grace
     * period, mirroring PaymentStatusPollingService. The webhook normally settles a Razorpay top-up
     * first; the poll is the safety net for a webhook that never arrives (not configured, wrong
     * secret, server unreachable), which would otherwise leave the wallet uncredited for good.
     * Invoked by WalletTopupStatusPollingScheduler.
     */
    public void pollPendingTopups() {
        List<WalletTopupStatus> pollableStatuses = List.of(WalletTopupStatus.INITIATED);
        List<PaymentGatewayType> pollableGateways = List.of(
                PaymentGatewayType.PAYU, PaymentGatewayType.TOUCAN, PaymentGatewayType.PINELABS, PaymentGatewayType.RAZORPAY);
        List<WalletTopupSession> candidates =
                walletTopupSessionRepository.findByStatusInAndGatewayIn(pollableStatuses, pollableGateways);

        LocalDateTime maxAgeCutoff = LocalDateTime.now().minusHours(paymentPollingProperties.getMaxAgeHours());
        LocalDateTime razorpayGraceCutoff = LocalDateTime.now().minusSeconds(paymentPollingProperties.getRazorpayGraceSeconds());
        int captured = 0, failed = 0, expired = 0, stillPending = 0, errored = 0;

        for (WalletTopupSession session : candidates) {
            // Give the Razorpay webhook first refusal on a top-up that has only just been created.
            if (session.getGateway() == PaymentGatewayType.RAZORPAY
                    && session.getCreatedAt() != null && session.getCreatedAt().isAfter(razorpayGraceCutoff)) {
                stillPending++;
                continue;
            }
            if (session.getCreatedAt() != null && session.getCreatedAt().isBefore(maxAgeCutoff)) {
                session.setStatus(WalletTopupStatus.EXPIRED);
                session.setUpdatedAt(LocalDateTime.now());
                walletTopupSessionRepository.save(session);
                expired++;
                continue;
            }

            GatewayStatusResult result;
            try {
                StatusCheckGatewayClient client = gatewayClientResolver.resolveStatusCheck(session.getGateway());
                result = client.fetchStatus(transientPaymentFor(session));
            } catch (Exception e) {
                log.warn("Status-check failed for wallet topup {} (gateway={}): {} — leaving unchanged, will retry next run",
                        session.getId(), session.getGateway(), e.getMessage());
                errored++;
                continue;
            }

            if (GatewayClientConstants.RESULT_SUCCESS.equals(result.status())) {
                if (captureIfVerified(session, result.providerPaymentId(),
                        GatewayAmountVerifier.toPaiseOrNull(result.amount()), result.currency())) captured++;
            } else if (GatewayClientConstants.RESULT_FAILED.equals(result.status())) {
                if (failIfNotAlready(session, "Gateway reported failure status: " + result.rawProviderStatus())) failed++;
            } else {
                stillPending++;
            }
        }

        log.info("Wallet-topup status polling: {} candidate(s) — captured={} failed={} expired={} stillPending={} errored={}",
                candidates.size(), captured, failed, expired, stillPending, errored);
    }

    /**
     * Marks the top-up paid and starts its wallet credit, only when the gateway-reported amount and currency match the
     * session. A mismatch credits nothing and flags the session AMOUNT_MISMATCH. Late captures of FAILED/EXPIRED
     * sessions are allowed (the gateway is the source of truth); PAID_AWAITING_CREDIT, CAPTURED and AMOUNT_MISMATCH
     * are never re-captured.
     */
    private boolean captureIfVerified(WalletTopupSession session, String providerPaymentId, Long reportedPaise,
                                      String reportedCurrency) {
        WalletTopupStatus status = session.getStatus();
        if (status != WalletTopupStatus.INITIATED && status != WalletTopupStatus.FAILED && status != WalletTopupStatus.EXPIRED) {
            return false;
        }
        if (conflictsWithRecordedPaymentId(session, providerPaymentId)) {
            return false;
        }
        if (!amountVerifier.matches(session.getId(), session.getAmount(), session.getCurrency(), reportedPaise, reportedCurrency)) {
            session.setStatus(WalletTopupStatus.AMOUNT_MISMATCH);
            session.setFailureReason("AMOUNT_MISMATCH: expectedPaise="
                    + (session.getAmount() == null ? null : GatewayAmountVerifier.toPaise(session.getAmount()))
                    + " reportedPaise=" + reportedPaise + " reportedCurrency=" + reportedCurrency);
            session.setUpdatedAt(LocalDateTime.now());
            walletTopupSessionRepository.save(session);
            return false;
        }
        if (providerPaymentId != null && (session.getProviderPaymentId() == null || session.getProviderPaymentId().isBlank())) {
            session.setProviderPaymentId(providerPaymentId);
        }
        session.setStatus(WalletTopupStatus.PAID_AWAITING_CREDIT);
        session.setFailureReason(null);
        session.setCreditNextAttemptAt(null);
        session.setUpdatedAt(LocalDateTime.now());
        WalletTopupSession paid = walletTopupSessionRepository.save(session);
        log.info("Wallet topup verified as paid: topupId={} customerId={} amount={} - crediting the wallet",
                paid.getId(), paid.getCustomerId(), paid.getAmount());
        // The only place a wallet credit is started: after a real, verified gateway confirmation, never off a
        // caller-supplied amount. One idempotent call keyed by the top-up id; if the wallet is down or refuses, the
        // top-up stays PAID_AWAITING_CREDIT and the credit job books it later.
        creditService.attemptCredit(paid.getId());
        return true;
    }

    /** A provider payment id that differs from the recorded one is never accepted; the event is ignored. */
    private boolean conflictsWithRecordedPaymentId(WalletTopupSession session, String providerPaymentId) {
        String recorded = session.getProviderPaymentId();
        boolean conflict = providerPaymentId != null && recorded != null && !recorded.isBlank()
                && !recorded.equals(providerPaymentId);
        if (conflict) {
            log.error("Wallet topup {} event carries provider payment id {} but {} is on record, ignoring the event",
                    session.getId(), providerPaymentId, recorded);
        }
        return conflict;
    }

    /** FAILED is only reachable from INITIATED: a late failure can never overwrite CAPTURED or another final state. */
    private boolean failIfNotAlready(WalletTopupSession session, String reason) {
        if (session.getStatus() != WalletTopupStatus.INITIATED) return false;
        session.setStatus(WalletTopupStatus.FAILED);
        session.setFailureReason(reason);
        session.setUpdatedAt(LocalDateTime.now());
        walletTopupSessionRepository.save(session);
        return true;
    }

    /** The paid amount in paise, or null when absent or not a whole number of paise. */
    private static Long reportedPaise(JSONObject paymentEntity) {
        Object raw = paymentEntity.opt("amount");
        if (!(raw instanceof Number number)) {
            return null;
        }
        BigDecimal value = new BigDecimal(number.toString());
        return value.stripTrailingZeros().scale() > 0 ? null : value.longValue();
    }

    private void recordWebhookEvent(String eventId, String topupId, String eventType, WebhookOutcome outcome) {
        String rowId = UUID.randomUUID().toString();
        webhookEventRepository.save(PaymentWebhookEvent.builder()
                .id(rowId)
                .providerEventId(eventId)
                .claimedEventId(eventId)
                .provider(GatewayAuditConstants.PROVIDER_RAZORPAY)
                .signatureValid(true)
                .outcome(outcome.name())
                .paymentId(topupId)
                .eventType(eventType)
                .processedAt(LocalDateTime.now())
                .build());
        String tenantId = topupId == null ? null
                : walletTopupSessionRepository.findById(topupId).map(WalletTopupSession::getTenantId).orElse(null);
        webhookAudit.publishProcessed(rowId, new WebhookObservation(GatewayAuditConstants.PROVIDER_RAZORPAY, eventType,
                eventId, true, outcome, topupId, tenantId));
    }

    private WalletTopupResponse replayTopup(CreateWalletTopupRequest req, String originApp, WalletTopupSession previous) {
        if (!previous.getCustomerId().equals(req.customerId()) || !TenantAccess.canSee(previous.getTenantId())
                || previous.getAmount().compareTo(MoneyRequests.valueIn(req.amount(), amountVerifier.currency())) != 0
                || (req.appCode() != null && previous.getAppCode() != null && !previous.getAppCode().equals(originApp))) {
            throw new ConflictException("Idempotency key is already used by another top-up");
        }
        return WalletTopupResponse.from(previous);
    }

    private Payment transientPaymentFor(WalletTopupSession session) {
        return Payment.builder()
                .internalPaymentId(session.getId())
                .rideId(session.getId())
                .customerId(session.getCustomerId())
                .amount(session.getAmount())
                .currency(session.getCurrency())
                .gateway(session.getGateway())
                .providerOrderId(session.getProviderOrderId())
                .providerPaymentId(session.getProviderPaymentId())
                .build();
    }
}
