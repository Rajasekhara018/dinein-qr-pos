package com.heuristiq.payment.payment.service;

import com.heuristiq.payment.shared.money.MoneyRequests;
import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.payment.config.MoneyProperties;
import com.heuristiq.payment.shared.PaymentCommonConstants;
import com.heuristiq.phone.PhoneNumbers;
import com.heuristiq.payment.client.RideServiceClient;
import com.heuristiq.payment.client.dto.RideDetailsResponse;
import com.heuristiq.payment.config.PaymentFareProperties;
import com.heuristiq.payment.payment.dto.CreatePaymentRequest;
import com.heuristiq.payment.payment.dto.PaymentResponse;
import com.heuristiq.payment.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayRouter;
import com.heuristiq.payment.payment.PaymentConstants;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStateMachine;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.security.AuthPrincipal;
import com.heuristiq.payment.security.TenantAccess;
import com.heuristiq.payment.shared.exception.NotFoundException;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Payment creation and retrieval — the INITIATED -> AUTHORIZED -> CAPTURED -> SETTLED state
 * machine's entry point (HLD §17.2), cash and gateway-checkout-session flows. Webhook handling,
 * refunds, settlement, and gateway status-polling each live in their own focused collaborator now
 * (PaymentWebhookHandler / PaymentRefundService / PaymentSettlementService /
 * PaymentStatusPollingService).
 *
 * HLD §23.3 hard rule honored here: a payment is NEVER marked CAPTURED/SETTLED
 * from the client's own response to /payments (the gateway branch just returns the
 * checkout session) — the transition to CAPTURED only happens inside PaymentWebhookHandler or the
 * status poller, driven by the gateway's own server-to-server answer. CASH is the one exception:
 * cash collection is attested by the driver/rider app after the fact, so it is captured
 * synchronously (mirrors the monolith's CashPaymentService.confirmCashPayment).
 *
 * <p>The gateway call never runs inside a database transaction: the INITIATED payment is persisted
 * first, the gateway session is created next, and the order id is stored afterwards. A gateway failure
 * therefore leaves a FAILED payment row, never an order at the gateway without a row.
 *
 * <p>Amount and ownership: the amount must equal the fare Rider_RideService holds for the ride
 * (see {@link PaymentFareProperties}), and a normal caller may only create a payment for their own
 * ride (customer) or a ride they drive (driver).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RideServiceClient rideServiceClient;
    private final PaymentGatewayClientResolver gatewayClientResolver;
    private final PaymentGatewayRouter gatewayRouter;
    private final PaymentTransitionEffects transitionEffects;
    private final PaymentFareProperties fareProperties;
    private final TransactionTemplate tx;
    private final MoneyProperties moneyProperties;

    /** Creates a payment on behalf of a trusted in-process caller (no ownership check). */
    public PaymentResponse createPayment(CreatePaymentRequest req) {
        return createPayment(req, AuthPrincipal.SYSTEM);
    }

    /**
     * Creates a payment for the authenticated {@code caller}.
     *
     * @throws AccessDeniedException   the caller is not the ride's customer or driver (HTTP 403)
     * @throws PaymentServiceException unsupported method, or the amount differs from the ride fare (HTTP 400)
     */
    public PaymentResponse createPayment(CreatePaymentRequest req, AuthPrincipal caller) {
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), caller);
        }

        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(req.method().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PaymentServiceException("Unsupported payment method: " + req.method());
        }
        if (method == PaymentMethod.UPI_DIRECT) {
            // recorded by the ride service once the driver confirms it (InternalDirectCollectionController), never created here
            throw new PaymentServiceException(PaymentConstants.MSG_UPI_DIRECT_NOT_CREATABLE);
        }

        boolean isTest = Boolean.TRUE.equals(req.testTransaction());
        RideDetailsResponse ride = null;
        if (req.isExternal()) {
            // Another Rider application's payment: there is no ride to look up or fare to check. Only a backend
            // service (or an admin) may raise one, and the caller states the amount it is charging.
            if (!caller.isPrivileged()) {
                throw new AccessDeniedException("Access denied: only a backend service may create an external payment");
            }
            if (req.sourceReference() == null || req.sourceReference().isBlank()) {
                throw new PaymentServiceException("sourceReference is required for an external payment");
            }
        } else {
            if (req.rideId() == null || req.rideId().isBlank()) {
                throw new PaymentServiceException("rideId is required");
            }
            ride = isTest ? fetchRideIfPresent(req.rideId()) : requireRide(req.rideId());
            authorizeCaller(req, ride, caller);
            if (!isTest) {
                verifyFare(req, ride);
            }
        }

        Payment payment = Payment.builder()
                .internalPaymentId(UUID.randomUUID().toString())
                .tenantId(TenantAccess.stamp())
                .rideId(req.isExternal() ? req.sourceService() + ":" + req.sourceReference() : req.rideId())
                .sourceService(req.isExternal() ? req.sourceService().trim() : null)
                .sourceReference(req.isExternal() ? req.sourceReference().trim() : null)
                .customerId(req.customerId())
                .customerName(blankToNull(req.customerName()))
                .customerEmail(blankToNull(req.customerEmail()))
                .customerMobile(mobileForStorage(blankToNull(req.customerMobile())))
                .driverId(ride != null ? ride.driverId() : null)
                .amount(MoneyRequests.valueIn(req.amount(), moneyProperties.getCurrency()))
                .currency(moneyProperties.getCurrency())
                .method(method)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(req.idempotencyKey())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (method == PaymentMethod.CASH) {
            try {
                return tx.execute(status -> captureCash(payment));
            } catch (DataIntegrityViolationException e) {
                Payment winner = paymentRepository.findByIdempotencyKey(req.idempotencyKey()).orElseThrow(() -> e);
                return replay(winner, caller);
            }
        }

        PaymentGatewayType gatewayType = gatewayRouter.resolve(req.gateway());
        payment.setGateway(gatewayType);
        return createGatewaySession(payment, gatewayType, caller);
    }

    /** A repeated idempotency key returns the existing payment, but only to someone allowed to see it. */
    private PaymentResponse replay(Payment payment, AuthPrincipal caller) {
        if (!caller.isPrivileged() && !caller.isSelf(payment.getCustomerId()) && !caller.isSelf(payment.getDriverId())) {
            throw new AccessDeniedException("Access denied: idempotency key belongs to another payment");
        }
        log.info("Idempotent replay of POST /payments — returning existing payment {}", payment.getInternalPaymentId());
        return PaymentResponse.from(payment);
    }

    /**
     * Normal callers: the customer may pay for themselves, the driver of the ride may record a payment for
     * it, and the ride's customer must be the payer. Admin and service callers are exempt.
     */
    private void authorizeCaller(CreatePaymentRequest req, RideDetailsResponse ride, AuthPrincipal caller) {
        if (caller.isPrivileged()) {
            return;
        }
        if (ride != null && ride.customerId() != null && !ride.customerId().equals(req.customerId())) {
            throw new AccessDeniedException("Access denied: the ride belongs to another customer");
        }
        boolean customerSelf = caller.isSelf(req.customerId());
        boolean rideDriver = caller.isDriver() && ride != null && caller.isSelf(ride.driverId());
        if (!customerSelf && !rideDriver) {
            throw new AccessDeniedException("Access denied: you may only create payments for your own rides");
        }
    }

    /** The charged amount must be the fare Rider_RideService holds for the ride, not the caller's number. */
    private void verifyFare(CreatePaymentRequest req, RideDetailsResponse ride) {
        if (!fareProperties.isEnabled()) {
            return;
        }
        Money rideFare = ride.totalAmount();
        BigDecimal fare = rideFare == null ? null : MoneyRequests.valueIn(rideFare, Money.codeOf(req.amount()));
        if (fare == null) {
            throw new PaymentServiceException("Ride " + req.rideId() + " has no fare amount yet");
        }
        if (fare.subtract(req.amount().value()).abs().compareTo(fareProperties.getToleranceRupees()) > 0) {
            log.warn("Rejected payment for ride {}: requested amount {} differs from ride fare {}",
                    req.rideId(), req.amount(), fare);
            throw new PaymentServiceException("Payment amount " + req.amount() + " does not match the ride fare " + fare);
        }
    }

    /** The payer's mobile as stored for new payments: E.164 when it parses (a bare number is read as the default region's), else as given. */
    private static String mobileForStorage(String mobile) {
        return mobile == null ? null : PhoneNumbers.toE164(mobile).orElse(mobile);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RideDetailsResponse requireRide(String rideId) {
        return rideServiceClient.getRide(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found: " + rideId));
    }

    /**
     * Test-transaction variant of {@link #requireRide(String)}: a rideId that Rider_RideService
     * doesn't recognize (including the 404 WebClient throws today rather than returning empty —
     * see RideServiceClient#getRide) no longer fails the payment. Used only when the caller
     * explicitly marks the request as a test transaction, so this never masks a real booking's
     * ride-lookup failure.
     */
    private RideDetailsResponse fetchRideIfPresent(String rideId) {
        try {
            return rideServiceClient.getRide(rideId).orElse(null);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Test transaction: rideId={} not found in Rider_RideService — proceeding without ride validation", rideId);
            return null;
        }
    }

    private PaymentResponse captureCash(Payment payment) {
        PaymentStateMachine.apply(payment, PaymentStatus.CAPTURED);
        payment.setInvoiceNumber("INV-" + payment.getInternalPaymentId().substring(0, 8).toUpperCase());
        Payment saved = paymentRepository.save(payment);
        log.info("Cash payment captured: paymentId={} rideId={} amount={}",
                saved.getInternalPaymentId(), saved.getRideId(), saved.getAmount());

        transitionEffects.onPaymentCaptured(saved);
        return PaymentResponse.from(saved);
    }

    /**
     * Routes checkout-session creation through the gateway abstraction for ANY of the 4 supported
     * gateways. HLD §23.3 hard rule still honored: nothing here marks the payment CAPTURED/SETTLED
     * — only providerOrderId is set. Order of work: persist INITIATED, call the gateway (no
     * transaction open), store the order id. A gateway failure marks the row FAILED and rethrows.
     */
    private PaymentResponse createGatewaySession(Payment payment, PaymentGatewayType gatewayType, AuthPrincipal caller) {
        Payment persisted;
        try {
            persisted = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            Payment winner = paymentRepository.findByIdempotencyKey(payment.getIdempotencyKey()).orElseThrow(() -> e);
            return replay(winner, caller);
        }

        GatewaySessionResult result;
        try {
            CreateSessionGatewayClient client = gatewayClientResolver.resolveCreateSession(gatewayType);
            result = client.createSession(persisted);
        } catch (RuntimeException e) {
            log.error("Checkout-session creation failed for payment {} ({}): {}",
                    persisted.getInternalPaymentId(), gatewayType, e.getMessage());
            if (PaymentStateMachine.apply(persisted, PaymentStatus.FAILED)) {
                persisted.setFailureReason("Gateway session creation failed");
                paymentRepository.save(persisted);
            }
            throw e;
        }

        persisted.setProviderOrderId(result.providerOrderId());
        persisted.setUpdatedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(persisted);
        log.info("Created {} checkout session (providerOrderId={}) for payment {}",
                gatewayType, result.providerOrderId(), saved.getInternalPaymentId());

        return PaymentResponse.from(saved, result);
    }

    public PaymentResponse getPayment(String paymentId) {
        return PaymentResponse.from(getPaymentEntity(paymentId));
    }

    public Payment getPaymentEntity(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
    }
}
