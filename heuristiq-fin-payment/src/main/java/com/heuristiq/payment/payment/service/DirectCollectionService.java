package com.heuristiq.payment.payment.service;

import com.heuristiq.payment.config.MoneyProperties;
import com.heuristiq.payment.payment.PaymentConstants;
import com.heuristiq.payment.payment.dto.DirectCollectionRequest;
import com.heuristiq.payment.payment.dto.PaymentResponse;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStateMachine;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.security.TenantAccess;
import com.heuristiq.payment.shared.exception.ConflictException;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import com.heuristiq.payment.shared.money.MoneyRequests;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records a direct collection: a ride fare the rider paid to the driver in cash or to the driver's own UPI id
 * (docs/DRIVER_FRANCHISE_SUBSCRIPTION_PLAN.md, D12-D13). Called by Rider_RideService once the driver confirmed it. The record is a
 * CAPTURED payment (method CASH or UPI_DIRECT) with a receipt, so it appears in the payment lists, summaries and receipts. No gateway
 * is called, the ride is not told (it is already paid) and the driver's earnings are not computed here: the ledger's sale journal
 * books them. One record per ride (idempotency key {@code direct-collection:<rideId>}), in the caller's organization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectCollectionService {

    private final PaymentRepository payments;
    private final PaymentTransitionEffects effects;
    private final MoneyProperties moneyProperties;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** What the call did: the payment, and whether it was recorded before. */
    public record Recorded(PaymentResponse payment, boolean replayed) {
    }

    /**
     * @throws PaymentServiceException (400) when the caller names no organization, or the amount is in another currency
     * @throws ConflictException       (409) when the ride's collection was recorded for another organization
     */
    public Recorded record(DirectCollectionRequest request) {
        String tenantId = TenantAccess.stamp();
        if (tenantId == null) {
            throw new PaymentServiceException(PaymentConstants.MSG_DIRECT_COLLECTION_TENANT_REQUIRED);
        }
        String key = PaymentConstants.DIRECT_COLLECTION_KEY_PREFIX + request.rideId().trim();
        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return replay(existing.get(), tenantId);
        }
        Payment payment = newPayment(request, tenantId, key);
        try {
            Payment saved = tx.execute(status -> {
                Payment stored = payments.save(payment);
                effects.onDirectCollectionRecorded(stored);
                return stored;
            });
            log.info("Direct collection recorded: paymentId={} rideId={} method={} amount={}",
                    saved.getInternalPaymentId(), saved.getRideId(), saved.getMethod(), saved.getAmount());
            return new Recorded(PaymentResponse.from(saved), false);
        } catch (DataIntegrityViolationException e) {
            // a concurrent repeat of the same ride won the insert: answer with its record
            Payment winner = payments.findByIdempotencyKey(key).orElseThrow(() -> e);
            return replay(winner, tenantId);
        }
    }

    private Payment newPayment(DirectCollectionRequest request, String tenantId, String key) {
        LocalDateTime now = LocalDateTime.now(clock);
        String paymentId = UUID.randomUUID().toString();
        PaymentMethod method = methodOf(request.channel());
        Payment payment = Payment.builder()
                .internalPaymentId(paymentId)
                .tenantId(tenantId)
                .rideId(request.rideId().trim())
                .customerId(blankToNull(request.customerId()))
                .driverId(request.driverId().trim())
                .amount(MoneyRequests.valueIn(request.amount(), moneyProperties.getCurrency()))
                .currency(moneyProperties.getCurrency())
                .method(method)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(key)
                .createdAt(now)
                .updatedAt(now)
                .build();
        PaymentStateMachine.apply(payment, PaymentStatus.CAPTURED);
        if (method == PaymentMethod.CASH) {
            // the monolith-era cash invoice number, kept for cash payments as on the POST /payments cash path
            payment.setInvoiceNumber(PaymentConstants.INVOICE_PREFIX + paymentId.substring(0, 8).toUpperCase(Locale.ROOT));
        }
        return payment;
    }

    /** A repeat for the same ride answers its record, but only within the organization it was recorded in. */
    private static Recorded replay(Payment payment, String tenantId) {
        if (payment.getTenantId() != null && !payment.getTenantId().equals(tenantId)) {
            throw new ConflictException(PaymentConstants.MSG_DIRECT_COLLECTION_OTHER_TENANT);
        }
        return new Recorded(PaymentResponse.from(payment), true);
    }

    /** UPI (to the driver's own UPI id) is UPI_DIRECT; anything else the request validation let through is CASH. */
    static PaymentMethod methodOf(String channel) {
        return PaymentConstants.DIRECT_COLLECTION_CHANNEL_UPI.equalsIgnoreCase(channel == null ? null : channel.trim())
                ? PaymentMethod.UPI_DIRECT : PaymentMethod.CASH;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
