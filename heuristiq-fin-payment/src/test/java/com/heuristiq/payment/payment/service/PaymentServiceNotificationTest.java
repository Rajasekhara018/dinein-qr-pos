package com.heuristiq.payment.payment.service;

import java.math.BigDecimal;
import com.heuristiq.payment.client.NmsServiceClient;
import com.heuristiq.payment.client.RideServiceClient;
import com.heuristiq.payment.client.dto.NotificationRequest;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayRouter;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused test for the NMS "Payment Result" notification wiring on the cash-capture path
 * (PaymentService -> PaymentTransitionEffects.onPaymentCaptured). The webhook-driven equivalent
 * (payment.failed with NMS unavailable) now lives in PaymentWebhookHandlerTest, since
 * handleWebhook moved to its own class — see PaymentWebhookHandler's javadoc.
 */
class PaymentServiceNotificationTest {

    private PaymentRepository paymentRepository;
    private RideServiceClient rideServiceClient;
    private NmsServiceClient nmsServiceClient;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        rideServiceClient = mock(RideServiceClient.class);
        nmsServiceClient = mock(NmsServiceClient.class);
        PaymentGatewayClientResolver gatewayClientResolver = mock(PaymentGatewayClientResolver.class);
        PaymentGatewayRouter gatewayRouter = mock(PaymentGatewayRouter.class);
        PaymentTransitionEffects transitionEffects = new PaymentTransitionEffects(
                paymentRepository, rideServiceClient, nmsServiceClient, org.mockito.Mockito.mock(com.heuristiq.payment.salesettlement.application.SaleSettlementService.class));

        paymentService = new PaymentService(paymentRepository, rideServiceClient,
                gatewayClientResolver, gatewayRouter, transitionEffects, new com.heuristiq.payment.config.PaymentFareProperties(),
                new org.springframework.transaction.support.TransactionTemplate(
                        mock(org.springframework.transaction.PlatformTransactionManager.class)),
                new com.heuristiq.payment.config.MoneyProperties());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cashPaymentCaptured_notifiesCustomer_withPaymentSuccessfulEvent() {
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(rideServiceClient.getRide("ride-1")).thenReturn(Optional.of(
                new com.heuristiq.payment.client.dto.RideDetailsResponse(
                        "ride-1", "cust-1", "driver-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("150.0"), "INR"), null, "RIDE_COMPLETED")));

        paymentService.createPayment(new com.heuristiq.payment.payment.dto.CreatePaymentRequest(
                "ride-1", "cust-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("150.0"), "INR"), "CASH", "idem-1", null, null, null, null, null));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(nmsServiceClient).notify(captor.capture());
        NotificationRequest sent = captor.getValue();
        assertEquals("PAYMENT_SUCCESSFUL", sent.getEvent());
        assertEquals("cust-1", sent.getUserIds().get(0));
    }
}
