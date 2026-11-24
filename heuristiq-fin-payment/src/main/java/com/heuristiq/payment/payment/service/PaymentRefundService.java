package com.heuristiq.payment.payment.service;

import com.heuristiq.payment.shared.money.MoneyRequests;
import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.payment.config.MoneyProperties;
import com.heuristiq.payment.payment.PaymentConstants;
import com.heuristiq.payment.payment.dto.PaymentResponse;
import com.heuristiq.payment.payment.dto.RefundRequest;
import com.heuristiq.payment.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayRouter;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentRefund;
import com.heuristiq.payment.payment.model.PaymentStateMachine;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.model.RefundDestination;
import com.heuristiq.payment.payment.model.RefundStatus;
import com.heuristiq.payment.payment.repository.PaymentRefundRepository;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.shared.exception.ConflictException;
import com.heuristiq.payment.shared.exception.NotFoundException;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import com.heuristiq.payment.shared.exception.RefundOutcomeUnknownException;
import com.heuristiq.payment.shared.money.AmountCurrency;
import com.heuristiq.payment.wallet.port.WalletCreditRejectedException;
import com.heuristiq.payment.wallet.port.WalletRefundCreditClient;
import com.heuristiq.payment.wallet.port.WalletRefundCreditCommand;
import com.heuristiq.payment.wallet.service.WalletService;
import com.heuristiq.resilient.DownstreamUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Refund engine for CAPTURED, SETTLED and PARTIALLY_REFUNDED payments, designed like the train refund
 * engine: marker first, idempotent gateway call outside any database transaction, result recorded after.
 *
 * <ol>
 *   <li><b>Reserve</b> (short transaction, payment row locked): validate, work out the destination, check
 *       the amount against the remaining refundable amount (payment amount minus all PROCESSING and
 *       COMPLETED refunds) and save a PROCESSING refund row. Two concurrent requests serialise on the
 *       payment lock, so they cannot both pass the check.</li>
 *   <li><b>Pay out</b> (no transaction): for a GATEWAY destination call the gateway, idempotent on the
 *       refund reference. For a WALLET destination credit the customer's wallet in Rider_WalletService through
 *       {@link WalletRefundCreditClient}, idempotent on the refund id (the wallet owns every balance; nothing is
 *       written to the legacy {@code wallet_transactions} table).</li>
 *   <li><b>Record</b> (short transaction, payment row locked): mark the refund COMPLETED (keeping the wallet's journal
 *       id) and move the payment to PARTIALLY_REFUNDED or REFUNDED.</li>
 * </ol>
 *
 * <p>Each refund has exactly one destination: it never refunds at the gateway and also credits the
 * wallet. A definite gateway or wallet rejection marks the refund FAILED and releases its amount. An unknown
 * outcome (gateway timeout/5xx, wallet unavailable) leaves it PROCESSING; repeating the request with the same
 * reference resumes it (the wallet credit is idempotent, so it can not be booked twice).
 */
@Slf4j
@Component
public class PaymentRefundService {

    private static final int KEY_MAX = 255;

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final WalletService walletService;
    private final WalletRefundCreditClient walletRefundCreditClient;
    private final PaymentGatewayClientResolver gatewayClientResolver;
    private final PaymentGatewayRouter gatewayRouter;
    private final TransactionTemplate tx;
    private final MoneyProperties moneyProperties;

    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.payment.refundaccounting.application.RefundAccountingService refundAccounting;

    public PaymentRefundService(PaymentRepository paymentRepository,
                                PaymentRefundRepository refundRepository,
                                WalletService walletService,
                                WalletRefundCreditClient walletRefundCreditClient,
                                PaymentGatewayClientResolver gatewayClientResolver,
                                PaymentGatewayRouter gatewayRouter,
                                TransactionTemplate transactionTemplate,
                                MoneyProperties moneyProperties) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.walletService = walletService;
        this.walletRefundCreditClient = walletRefundCreditClient;
        this.gatewayClientResolver = gatewayClientResolver;
        this.gatewayRouter = gatewayRouter;
        this.tx = transactionTemplate;
        this.moneyProperties = moneyProperties;
    }

    /** What the reserve step decided: the refund row and the payment it belongs to. */
    private record Reservation(PaymentRefund refund, Payment payment) {
    }

    /** Refund on behalf of the authenticated caller (recorded as {@code created_by}). */
    public PaymentResponse refund(String paymentId, RefundRequest req) {
        return refund(paymentId, req, currentActor());
    }

    /**
     * Refunds a payment, fully or partially.
     *
     * @param actor who asked for the refund, stored in the refund ledger
     * @throws NotFoundException        no such payment
     * @throws ConflictException        over-refund, fully refunded payment, or reference reuse (HTTP 409)
     * @throws PaymentServiceException  invalid destination, ineligible payment status, or a definite gateway
     *                                  rejection (HTTP 400)
     */
    public PaymentResponse refund(String paymentId, RefundRequest req, String actor) {
        return refund(paymentId, req, actor, null);
    }

    /**
     * As {@link #refund(String, RefundRequest, String)}, additionally idempotent on {@code idempotencyKey}
     * (the HTTP Idempotency-Key header): the same key for the same payment returns the original refund,
     * the same key with a different amount, destination or payment is a 409, and a null/blank key keeps
     * the existing behaviour.
     */
    public PaymentResponse refund(String paymentId, RefundRequest req, String actor, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        Reservation reservation;
        try {
            reservation = tx.execute(status -> reserve(paymentId, req, actor != null ? actor : currentActor(), key));
        } catch (DataIntegrityViolationException e) {
            if (key == null) {
                throw e;
            }
            // Lost a race on the unique index (e.g. the same key on two different payments).
            throw new ConflictException("Idempotency-Key is already in use by another refund request");
        }
        PaymentRefund refund = reservation.refund();
        Payment payment = reservation.payment();

        if (refund.getStatus() == RefundStatus.COMPLETED) {
            return PaymentResponse.from(payment);
        }
        if (refund.getStatus() == RefundStatus.FAILED) {
            throw new ConflictException("Refund " + refund.getRefundReference()
                    + " previously failed; use a new reference to try again");
        }

        GatewayRefundResult gatewayResult = null;
        if (refund.getDestination() == RefundDestination.GATEWAY) {
            gatewayResult = callGateway(payment, refund);
        }
        final GatewayRefundResult result = gatewayResult;
        final String walletJournalId = refund.getDestination() == RefundDestination.WALLET
                ? creditWallet(payment, refund) : null;
        Payment saved = tx.execute(status -> complete(paymentId, refund.getId(), result, walletJournalId));
        log.info("Refund {} of payment {} completed: amount={} destination={}", refund.getRefundReference(),
                paymentId, refund.getAmount(), refund.getDestination());
        return PaymentResponse.from(saved);
    }

    private Reservation reserve(String paymentId, RefundRequest req, String actor, String idempotencyKey) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));

        if (idempotencyKey != null) {
            var byKey = refundRepository.findByIdempotencyKey(idempotencyKey);
            if (byKey.isPresent()) {
                return replay(byKey.get(), payment, req);
            }
        }

        String reference = normalizeReference(req.reference());
        if (reference != null) {
            var existing = refundRepository.findByRefundReference(reference);
            if (existing.isPresent()) {
                return replay(existing.get(), payment, req);
            }
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ConflictException("Payment is already fully refunded");
        }
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.SETTLED
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentServiceException("Only CAPTURED, SETTLED or PARTIALLY_REFUNDED payments can be refunded"
                    + " (current status: " + payment.getStatus() + ")");
        }

        RefundDestination destination = resolveDestination(payment, req.destination());
        if (destination == RefundDestination.WALLET && isBlank(payment.getCustomerId())) {
            throw new PaymentServiceException("Cannot refund to the wallet: payment has no customer on record");
        }
        if (destination == RefundDestination.GATEWAY && isBlank(payment.getProviderPaymentId())) {
            throw new PaymentServiceException("Cannot refund via the gateway: payment has no providerPaymentId on record");
        }

        BigDecimal paymentAmount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        BigDecimal committed = refundRepository.sumAmount(paymentId, EnumSet.of(RefundStatus.PROCESSING, RefundStatus.COMPLETED))
                .add(walletService.getLegacyRefundTotal(paymentId));
        BigDecimal remaining = round2(paymentAmount.subtract(committed));

        BigDecimal amount;
        if (req.amount() == null) {
            if (remaining.signum() <= 0) {
                throw new ConflictException("Nothing left to refund on this payment");
            }
            amount = remaining;
        } else {
            amount = round2(MoneyRequests.valueIn(req.amount(), paymentCurrency(payment)));
            if (amount.signum() <= 0) {
                throw new PaymentServiceException("Refund amount must be positive");
            }
            if (amount.compareTo(paymentAmount) > 0) {
                throw new PaymentServiceException("Refund amount exceeds the captured amount");
            }
            if (amount.compareTo(remaining) > 0) {
                throw new ConflictException("Refund amount exceeds the remaining refundable amount of " + remaining);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentRefund refund = refundRepository.save(PaymentRefund.builder()
                .id(UUID.randomUUID().toString())
                .refundReference(reference != null ? reference : "RF-" + UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .paymentId(paymentId)
                .tenantId(payment.getTenantId())
                .amount(amount)
                .amountCurrency(AmountCurrency.of(amount, payment.getCurrency(), moneyProperties.getCurrency()))
                .destination(destination)
                .status(RefundStatus.PROCESSING)
                .reason(req.reason())
                .createdBy(actor)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return new Reservation(refund, payment);
    }

    /** The currency of a payment (the deployment's for a row without one). */
    private String paymentCurrency(Payment payment) {
        return AmountCurrency.of(BigDecimal.ONE, payment.getCurrency(), moneyProperties.getCurrency());
    }

    /** Same reference again: return the existing refund after checking the request is the same one. */
    private Reservation replay(PaymentRefund existing, Payment payment, RefundRequest req) {
        if (!existing.getPaymentId().equals(payment.getInternalPaymentId())) {
            throw new ConflictException("Refund reference is already used for another payment");
        }
        if (req.amount() != null && round2(MoneyRequests.valueIn(req.amount(), paymentCurrency(payment)))
                .compareTo(existing.getAmount()) != 0) {
            throw new ConflictException("Refund reference is already used with a different amount");
        }
        if (!isBlank(req.destination()) && parseDestination(req.destination()) != existing.getDestination()) {
            throw new ConflictException("Refund reference is already used with a different destination");
        }
        log.info("Idempotent replay of refund {} for payment {} (status {})", existing.getRefundReference(),
                payment.getInternalPaymentId(), existing.getStatus());
        return new Reservation(existing, payment);
    }

    /** Calls the gateway outside any transaction. A definite rejection frees the amount; an unknown outcome does not. */
    private GatewayRefundResult callGateway(Payment payment, PaymentRefund refund) {
        PaymentGatewayType gatewayType = payment.getGateway() != null ? payment.getGateway() : gatewayRouter.defaultGatewayType();
        RefundGatewayClient client = gatewayClientResolver.resolveRefund(gatewayType);
        try {
            GatewayRefundResult result = client.refund(payment, refund.getAmount(), refund.getReason(), refund.getRefundReference());
            log.info("{} refund accepted for payment {}: refundId={} gatewayStatus={}", gatewayType,
                    payment.getInternalPaymentId(), result.refundId(), result.status());
            return result;
        } catch (RefundOutcomeUnknownException e) {
            log.warn("Refund {} of payment {} has an unknown gateway outcome; left PROCESSING for retry with the same reference",
                    refund.getRefundReference(), payment.getInternalPaymentId());
            throw e;
        } catch (PaymentServiceException e) {
            tx.executeWithoutResult(status -> markFailed(refund.getId(), e.getMessage()));
            throw e;
        }
    }

    /**
     * Credits the customer's wallet outside any transaction, idempotent on the refund id. A definite refusal frees the
     * amount (the refund becomes FAILED); an unavailable wallet leaves the refund PROCESSING so the same request can
     * resume it, and surfaces as 503.
     *
     * @return the wallet journal id, or null when the wallet did not report one
     */
    private String creditWallet(Payment payment, PaymentRefund refund) {
        String tenantId = refund.getTenantId() != null ? refund.getTenantId() : payment.getTenantId();
        try {
            String journalId = walletRefundCreditClient.credit(new WalletRefundCreditCommand(refund.getId(),
                    payment.getInternalPaymentId(), payment.getCustomerId(), tenantId,
                    Money.of(refund.getAmount(), refund.getAmountCurrency() != null ? refund.getAmountCurrency()
                            : paymentCurrency(payment))));
            log.info("Refund {} of payment {} credited to the wallet: journal={}", refund.getRefundReference(),
                    payment.getInternalPaymentId(), journalId);
            return journalId;
        } catch (WalletCreditRejectedException e) {
            tx.executeWithoutResult(status -> markFailed(refund.getId(), e.getMessage()));
            throw new PaymentServiceException("The wallet refused the refund credit"
                    + (e.errorCode() == null ? "" : " (" + e.errorCode() + ")") + "; use a new reference to try again");
        } catch (DownstreamUnavailableException e) {
            log.warn("Wallet unavailable while crediting refund {} of payment {}; left PROCESSING for retry with the same reference",
                    refund.getRefundReference(), payment.getInternalPaymentId());
            throw e;
        }
    }

    private void markFailed(String refundId, String message) {
        PaymentRefund refund = refundRepository.findById(refundId).orElseThrow();
        if (refund.getStatus() != RefundStatus.PROCESSING) {
            return;
        }
        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(truncate(message));
        refund.setUpdatedAt(LocalDateTime.now());
        refundRepository.save(refund);
    }

    private Payment complete(String paymentId, String refundId, GatewayRefundResult result, String walletJournalId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
        PaymentRefund refund = refundRepository.findById(refundId).orElseThrow();
        if (refund.getStatus() != RefundStatus.PROCESSING) {
            return payment;
        }

        refund.setStatus(RefundStatus.COMPLETED);
        refund.setUpdatedAt(LocalDateTime.now());
        if (result != null) {
            refund.setGatewayRefundId(result.refundId());
            refund.setGatewayStatus(result.status());
        }
        refund.setWalletJournalId(walletJournalId);
        refundRepository.save(refund);

        BigDecimal completed = refundRepository.sumAmount(paymentId, EnumSet.of(RefundStatus.COMPLETED))
                .add(walletService.getLegacyRefundTotal(paymentId));
        boolean full = completed.compareTo(payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount()) >= 0;
        PaymentStatus target = full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        if (payment.getStatus() != target) {
            PaymentStateMachine.apply(payment, target);
        }
        if (refundAccounting != null) {
            com.heuristiq.payment.salesettlement.domain.SaleEventIds.of(payment.getSourceService(), payment.getSourceReference(), payment.getRideId())
                    .ifPresent(event -> refundAccounting.record(new com.heuristiq.payment.refundaccounting.domain.RefundAccounting.Posting(
                            "payment-refund:" + refund.getId(), refund.getTenantId() != null ? refund.getTenantId() : payment.getTenantId(),
                            event, refund.getAmount(), paymentCurrency(payment), refund.getDestination() == RefundDestination.WALLET, 0)));
        }
        return paymentRepository.save(payment);
    }

    private RefundDestination resolveDestination(Payment payment, String requested) {
        // a direct collection (cash or UPI paid to the driver) never went through a gateway
        boolean cash = payment.getMethod() != null && payment.getMethod().isDirectCollection();
        if (isBlank(requested)) {
            return cash ? RefundDestination.WALLET : RefundDestination.GATEWAY;
        }
        RefundDestination destination = parseDestination(requested);
        if (destination == RefundDestination.GATEWAY && cash) {
            throw new PaymentServiceException("Cash payments cannot be refunded to the payment gateway; use destination WALLET");
        }
        return destination;
    }

    private static RefundDestination parseDestination(String value) {
        try {
            return RefundDestination.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaymentServiceException("Unsupported refund destination: " + value + " (use GATEWAY or WALLET)");
        }
    }

    private static String normalizeKey(String key) {
        if (isBlank(key)) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > KEY_MAX) {
            throw new PaymentServiceException("Idempotency-Key is longer than " + KEY_MAX + " characters");
        }
        return trimmed;
    }

    private static String normalizeReference(String reference) {
        if (isBlank(reference)) {
            return null;
        }
        String trimmed = reference.trim();
        if (trimmed.length() > PaymentConstants.MAX_REFUND_REFERENCE) {
            throw new PaymentServiceException("Refund reference is longer than " + PaymentConstants.MAX_REFUND_REFERENCE + " characters");
        }
        return trimmed;
    }

    private static String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.heuristiq.payment.security.AuthPrincipal principal) {
            return principal.userId();
        }
        return "system";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > PaymentConstants.MAX_FAILURE_REASON ? message.substring(0, PaymentConstants.MAX_FAILURE_REASON) : message;
    }

    private static BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
