package com.heuristiq.payment.payment.service;

import java.math.BigDecimal;
import com.heuristiq.payment.client.NmsServiceClient;
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
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.security.AuthPrincipal;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAY-01 (who may create a payment), PAY-04b (amount must equal the ride fare) and PAY-15 (the gateway
 * is called with no transaction open, after the INITIATED row is persisted).
 */
class PaymentServiceCreateTest {

    private PaymentRepository paymentRepository;
    private RideServiceClient rideServiceClient;
    private CreateSessionGatewayClient createSessionClient;
    private PaymentFareProperties fareProperties;
    private PaymentService paymentService;
    private final List<Payment> savedSnapshots = new ArrayList<>();

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        rideServiceClient = mock(RideServiceClient.class);
        PaymentGatewayClientResolver resolver = mock(PaymentGatewayClientResolver.class);
        PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
        createSessionClient = mock(CreateSessionGatewayClient.class);
        fareProperties = new PaymentFareProperties();
        PaymentTransitionEffects effects = new PaymentTransitionEffects(
                paymentRepository, rideServiceClient, mock(NmsServiceClient.class), org.mockito.Mockito.mock(com.heuristiq.payment.salesettlement.application.SaleSettlementService.class));

        when(router.resolve(any())).thenReturn(PaymentGatewayType.RAZORPAY);
        when(resolver.resolveCreateSession(PaymentGatewayType.RAZORPAY)).thenReturn(createSessionClient);
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            savedSnapshots.add(Payment.builder().status(p.getStatus()).providerOrderId(p.getProviderOrderId()).build());
            return p;
        });
        when(rideServiceClient.getRide("ride-1")).thenReturn(Optional.of(
                new RideDetailsResponse("ride-1", "cust-1", "driver-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("150.0"), "INR"), null, "RIDE_COMPLETED")));

        paymentService = new PaymentService(paymentRepository, rideServiceClient, resolver, router, effects,
                fareProperties, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new com.heuristiq.payment.config.MoneyProperties());
    }

    private static CreatePaymentRequest request(String customerId, BigDecimal amount, String method) {
        return new CreatePaymentRequest("ride-1", customerId, com.heuristiq.payment.shared.money.Money.of(amount, "INR"), method, "idem-1", null, null, null, null, null);
    }

    @Test
    void customerCanPayForOwnRide() {
        when(createSessionClient.createSession(any(Payment.class))).thenReturn(GatewaySessionResult.ofRazorpay("order_1", null));

        PaymentResponse response = paymentService.createPayment(
                request("cust-1", new BigDecimal("150.0"), "RAZORPAY"), new AuthPrincipal("cust-1", "CUSTOMER"));

        assertThat(response.providerOrderId()).isEqualTo("order_1");
        assertThat(response.status()).isEqualTo("INITIATED");
    }

    @Test
    void anotherCustomer_cannotCreateAPaymentForSomeoneElsesRide() {
        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-2", new BigDecimal("150.0"), "CASH"), new AuthPrincipal("cust-2", "CUSTOMER")))
                .isInstanceOf(AccessDeniedException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void customer_cannotCreateAPaymentOnBehalfOfAnotherCustomerId() {
        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-1", new BigDecimal("150.0"), "CASH"), new AuthPrincipal("cust-2", "CUSTOMER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void upiDirectIsNeverCreatedThroughThePaymentsApi() {
        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-1", new BigDecimal("150.0"), "upi_direct"), new AuthPrincipal("cust-1", "CUSTOMER")))
                .isInstanceOf(PaymentServiceException.class).hasMessageContaining("UPI_DIRECT");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void assignedDriver_canRecordACashPaymentForTheirRide() {
        PaymentResponse response = paymentService.createPayment(
                request("cust-1", new BigDecimal("150.0"), "CASH"), new AuthPrincipal("driver-1", "DRIVER"));

        assertThat(response.status()).isEqualTo("CAPTURED");
    }

    @Test
    void unrelatedDriver_cannotRecordAPayment() {
        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-1", new BigDecimal("150.0"), "CASH"), new AuthPrincipal("driver-9", "DRIVER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminAndService_areExempt() {
        assertThat(paymentService.createPayment(request("cust-1", new BigDecimal("150.0"), "CASH"), new AuthPrincipal("admin-1", "ADMIN", true)).status())
                .isEqualTo("CAPTURED");
    }

    @Test
    void idempotentReplay_isNotReturnedToAStranger() {
        Payment existing = Payment.builder().internalPaymentId("p1").customerId("cust-1").driverId("driver-1")
                .method(com.heuristiq.payment.payment.model.PaymentMethod.CASH).status(PaymentStatus.CAPTURED).build();
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-2", new BigDecimal("150.0"), "CASH"), new AuthPrincipal("cust-2", "CUSTOMER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void amountThatDiffersFromTheRideFare_isRejected_beforeAnythingIsPersisted() {
        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-1", new BigDecimal("1.0"), "RAZORPAY"), new AuthPrincipal("cust-1", "CUSTOMER")))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("does not match the ride fare");

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(createSessionClient, never()).createSession(any(Payment.class));
    }

    @Test
    void finalFareTakesPrecedenceOverTheEstimate() {
        when(rideServiceClient.getRide("ride-1")).thenReturn(Optional.of(
                new RideDetailsResponse("ride-1", "cust-1", "driver-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("150.0"), "INR"), com.heuristiq.payment.shared.money.Money.of(new BigDecimal("180.0"), "INR"), "RIDE_COMPLETED")));

        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-1", new BigDecimal("150.0"), "CASH"), new AuthPrincipal("cust-1", "CUSTOMER")))
                .isInstanceOf(PaymentServiceException.class);
        assertThat(paymentService.createPayment(
                request("cust-1", new BigDecimal("180.0"), "CASH"), new AuthPrincipal("cust-1", "CUSTOMER")).amount().value()).isEqualByComparingTo("180.0");
    }

    @Test
    void fareVerificationCanBeSwitchedOff() {
        fareProperties.setEnabled(false);

        assertThat(paymentService.createPayment(
                request("cust-1", new BigDecimal("1.0"), "CASH"), new AuthPrincipal("cust-1", "CUSTOMER")).amount().value()).isEqualByComparingTo("1.0");
    }

    @Test
    void gatewayIsCalledOutsideATransaction_afterTheInitiatedRowIsPersisted() {
        boolean[] transactionActiveDuringGatewayCall = {true};
        when(createSessionClient.createSession(any(Payment.class))).thenAnswer(inv -> {
            transactionActiveDuringGatewayCall[0] = TransactionSynchronizationManager.isActualTransactionActive();
            return GatewaySessionResult.ofRazorpay("order_1", null);
        });

        paymentService.createPayment(request("cust-1", new BigDecimal("150.0"), "RAZORPAY"), new AuthPrincipal("cust-1", "CUSTOMER"));

        assertThat(transactionActiveDuringGatewayCall[0]).isFalse();
        InOrder order = inOrder(paymentRepository, createSessionClient);
        order.verify(paymentRepository).save(any(Payment.class));
        order.verify(createSessionClient).createSession(any(Payment.class));
        order.verify(paymentRepository).save(any(Payment.class));
        assertThat(savedSnapshots.get(0).getProviderOrderId()).isNull();
        assertThat(savedSnapshots.get(savedSnapshots.size() - 1).getProviderOrderId()).isEqualTo("order_1");
    }

    @Test
    void gatewayFailure_leavesAFailedRow_notAnOrphanOrder() {
        when(createSessionClient.createSession(any(Payment.class))).thenThrow(new PaymentServiceException("gateway down"));

        assertThatThrownBy(() -> paymentService.createPayment(
                request("cust-1", new BigDecimal("150.0"), "RAZORPAY"), new AuthPrincipal("cust-1", "CUSTOMER")))
                .isInstanceOf(PaymentServiceException.class);

        assertThat(savedSnapshots).extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.INITIATED, PaymentStatus.FAILED);
        assertThat(savedSnapshots.get(1).getProviderOrderId()).isNull();
    }
}
