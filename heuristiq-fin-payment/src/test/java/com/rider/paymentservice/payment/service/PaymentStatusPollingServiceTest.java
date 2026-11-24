package com.heuristiq.paymentservice.payment.service;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.client.NmsServiceClient;
import com.heuristiq.paymentservice.client.RideServiceClient;
import com.heuristiq.paymentservice.config.PaymentPollingProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentStatusPollingService — extracted out of PaymentService (see that class's javadoc).
 * pollPendingGatewayPayments() — how PAYU/TOUCAN/PINELABS payments (no webhook ingestion in this
 * service) ever learn their outcome. Proves: SUCCESS -> CAPTURED with the same side effects as
 * the webhook path, FAILED -> FAILED likewise, PENDING/exception leaves the payment untouched
 * (tried again next run). A too-old candidate is checked first: a paid one is CAPTURED, only a
 * still-pending one is EXPIRED. RAZORPAY payments are polled once past the grace period (the webhook
 * normally wins before that), and reconcile() resolves one payment on demand.
 */
class PaymentStatusPollingServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentGatewayClientResolver gatewayClientResolver;
    private StatusCheckGatewayClient statusCheckGatewayClient;
    private RideServiceClient rideServiceClient;
    private PlatformTransactionManager transactionManager;
    private PaymentPollingProperties paymentPollingProperties;
    private PaymentStatusPollingService paymentStatusPollingService;
    private final com.heuristiq.paymentservice.salesettlement.application.SaleSettlementService saleSettlement =
            mock(com.heuristiq.paymentservice.salesettlement.application.SaleSettlementService.class);

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        rideServiceClient = mock(RideServiceClient.class);
        NmsServiceClient nmsServiceClient = mock(NmsServiceClient.class);
        gatewayClientResolver = mock(PaymentGatewayClientResolver.class);
        statusCheckGatewayClient = mock(StatusCheckGatewayClient.class);
        transactionManager = mock(PlatformTransactionManager.class);
        paymentPollingProperties = new PaymentPollingProperties();
        paymentPollingProperties.setMaxAgeHours(24);
        paymentPollingProperties.setRazorpayGraceSeconds(120);
        PaymentTransitionEffects transitionEffects = new PaymentTransitionEffects(
                paymentRepository, rideServiceClient, nmsServiceClient, saleSettlement);

        paymentStatusPollingService = new PaymentStatusPollingService(
                paymentRepository, gatewayClientResolver, paymentPollingProperties, transitionEffects, new GatewayAmountVerifier(new com.heuristiq.paymentservice.config.MoneyProperties()),
                new TransactionTemplate(transactionManager));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** One page holding the given payments, each readable again by id inside its own transaction. */
    private void candidates(Payment... payments) {
        when(paymentRepository.findPollCandidatesAfter(any(), any(), eq(""), anyInt())).thenReturn(List.of(payments));
        for (Payment payment : payments) {
            when(paymentRepository.findByIdForUpdate(payment.getInternalPaymentId())).thenReturn(Optional.of(payment));
        }
    }

    private Payment payuPayment(PaymentStatus status, LocalDateTime createdAt) {
        return payuPayment("pay-internal-001", status, createdAt);
    }

    private Payment payuPayment(String id, PaymentStatus status, LocalDateTime createdAt) {
        return Payment.builder()
                .internalPaymentId(id)
                .rideId("ride-1")
                .customerId("cust-1")
                .amount(new BigDecimal("150.0"))
                .currency("INR")
                .method(PaymentMethod.RAZORPAY)
                .gateway(PaymentGatewayType.PAYU)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    @Test
    void gatewayReportsSuccess_transitionsToCapturedAndFiresSideEffects() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", "provider-pay-1", "SUCCESS",
                        "captured", new BigDecimal("150.0"), "INR"));
        when(rideServiceClient.getRide("ride-1")).thenReturn(java.util.Optional.empty());

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("provider-pay-1");
        // save() runs twice: the CAPTURED transition itself, then generateReceiptIfAbsent's own
        // save inside onPaymentCaptured — same double-save shape the cash-capture path already has.
        verify(paymentRepository, times(2)).save(payment);
        verify(saleSettlement).paymentCaptured("pay-internal-001", null, null, null, "ride-1");
    }

    @Test
    void gatewayReportsFailed_transitionsToFailedAndNotifiesRide() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "FAILED",
                        "declined", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(rideServiceClient, times(1)).notifyPaymentResult("ride-1", "FAILED", "pay-internal-001", com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("150.0"), "INR"));
        verify(saleSettlement).paymentAbandoned("pay-internal-001", null, null, null, "ride-1", "Gateway payment failed");
    }

    @Test
    void gatewayReportsPending_leavesPaymentUnchanged() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "PENDING",
                        "in_progress", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void statusCheckThrows_leavesPaymentUnchanged_triedAgainNextRun() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenThrow(new RuntimeException("gateway unreachable"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void tooOldPayment_gatewayStillPending_expires() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "PENDING", "created", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(paymentRepository, times(1)).save(payment);
        verify(saleSettlement).paymentAbandoned("pay-internal-001", null, null, null, "ride-1", "Gateway payment expired");
    }

    @Test
    void tooOldPayment_butPaidAtTheGateway_isCapturedNotExpired() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", "provider-pay-1", "SUCCESS", "paid", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("provider-pay-1");
    }

    @Test
    void razorpayPaymentWithinGracePeriod_isLeftToTheWebhook() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusSeconds(20));
        payment.setGateway(PaymentGatewayType.RAZORPAY);
        candidates(payment);

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(gatewayClientResolver, never()).resolveStatusCheck(any());
    }

    @Test
    void razorpayPaymentPastGracePeriod_whosWebhookNeverCame_isCapturedByThePoll() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(10));
        payment.setGateway(PaymentGatewayType.RAZORPAY);
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.RAZORPAY)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_1", "pay_1", "SUCCESS", "paid", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void reconcile_appliesTheGatewayOutcomeToOnePayment() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusSeconds(30));
        payment.setGateway(PaymentGatewayType.RAZORPAY);
        when(paymentRepository.findById("pay-internal-001")).thenReturn(java.util.Optional.of(payment));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.RAZORPAY)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_1", "pay_1", "SUCCESS", "paid", new BigDecimal("150.0"), "INR"));

        Payment result = paymentStatusPollingService.reconcile("pay-internal-001");

        // No grace period for an explicit request: the customer is asking right now.
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void reconcile_ofAnAlreadyCapturedPayment_doesNotAskTheGateway() {
        Payment payment = payuPayment(PaymentStatus.CAPTURED, LocalDateTime.now().minusMinutes(5));
        when(paymentRepository.findById("pay-internal-001")).thenReturn(java.util.Optional.of(payment));

        Payment result = paymentStatusPollingService.reconcile("pay-internal-001");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(gatewayClientResolver, never()).resolveStatusCheck(any());
    }

    @Test
    void alreadyResolvedByAnotherPath_idempotentGuardSkipsReapplyingTransition() {
        // Simulates the race the javadoc calls out: candidate was fetched while still INITIATED,
        // but something else (e.g. a concurrent run) already moved it to CAPTURED before this
        // iteration reached it. The re-check-before-applying guard must leave it alone.
        Payment payment = payuPayment(PaymentStatus.CAPTURED, LocalDateTime.now().minusMinutes(5));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", "provider-pay-1", "SUCCESS",
                        "captured", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void gatewayIsAskedBeforeAnyTransactionOpens_andEachPaymentGetsItsOwnTransaction() {
        Payment first = payuPayment("pay-a", PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48));
        Payment second = payuPayment("pay-b", PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48));
        candidates(first, second);
        paymentPollingProperties.setConcurrency(1);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "PENDING", "created", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(first.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        InOrder order = inOrder(statusCheckGatewayClient, transactionManager);
        order.verify(statusCheckGatewayClient).fetchStatus(first);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
        order.verify(statusCheckGatewayClient).fetchStatus(second);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void stillPendingPayment_neverOpensATransaction() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        payment.setGateway(PaymentGatewayType.RAZORPAY);
        payment.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        candidates(payment);

        paymentStatusPollingService.pollPendingGatewayPayments();

        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void candidatesAreReadPageByPage_untilAShortPage() {
        paymentPollingProperties.setPageSize(2);
        Payment a = payuPayment("pay-a", PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        Payment b = payuPayment("pay-b", PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        Payment c = payuPayment("pay-c", PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48));
        when(paymentRepository.findPollCandidatesAfter(any(), any(), eq(""), eq(2))).thenReturn(List.of(a, b));
        when(paymentRepository.findPollCandidatesAfter(any(), any(), eq("pay-b"), eq(2))).thenReturn(List.of(c));
        when(paymentRepository.findByIdForUpdate("pay-c")).thenReturn(Optional.of(c));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "PENDING", "created", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        verify(paymentRepository).findPollCandidatesAfter(any(), any(), eq(""), eq(2));
        verify(paymentRepository).findPollCandidatesAfter(any(), any(), eq("pay-b"), eq(2));
        verify(paymentRepository, times(2)).findPollCandidatesAfter(any(), any(), any(), anyInt());
        verify(statusCheckGatewayClient, times(3)).fetchStatus(any(Payment.class));
        assertThat(c.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(a.getStatus()).isEqualTo(PaymentStatus.INITIATED);
    }

    @Test
    void paymentChangedWhileTheGatewayWasAsked_isLeftForTheNextRun() {
        Payment candidate = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        candidate.setVersion(3L);
        Payment changedMeanwhile = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        changedMeanwhile.setVersion(4L);
        when(paymentRepository.findPollCandidatesAfter(any(), any(), eq(""), anyInt())).thenReturn(List.of(candidate));
        when(paymentRepository.findByIdForUpdate("pay-internal-001")).thenReturn(Optional.of(changedMeanwhile));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(candidate)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", "provider-pay-1", "SUCCESS", "captured", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(changedMeanwhile.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void paymentResolvedElsewhereWhileTheGatewayWasAsked_isNotTransitionedTwice() {
        Payment candidate = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        Payment alreadyCaptured = payuPayment(PaymentStatus.CAPTURED, LocalDateTime.now().minusMinutes(5));
        when(paymentRepository.findPollCandidatesAfter(any(), any(), eq(""), anyInt())).thenReturn(List.of(candidate));
        when(paymentRepository.findByIdForUpdate("pay-internal-001")).thenReturn(Optional.of(alreadyCaptured));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(candidate)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "FAILED", "declined", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(alreadyCaptured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(rideServiceClient, never()).notifyPaymentResult(any(), any(), any(), any());
    }

    @Test
    void oneFailingTransaction_doesNotStopTheOthers() {
        Payment a = payuPayment("pay-a", PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48));
        Payment b = payuPayment("pay-b", PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48));
        candidates(a, b);
        when(paymentRepository.findByIdForUpdate("pay-a")).thenThrow(new IllegalStateException("db hiccup"));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "PENDING", "created", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        assertThat(a.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(b.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(transactionManager).rollback(any());
    }

    @Test
    void aRunThatHasUsedItsBudget_readsNoFurtherPages() {
        paymentPollingProperties.setRunBudget(java.time.Duration.ZERO);
        candidates(payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusHours(48)));

        paymentStatusPollingService.pollPendingGatewayPayments();

        verify(paymentRepository, never()).findPollCandidatesAfter(any(), any(), any(), anyInt());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void theReReadLocksThePaymentRow_soARacingWebhookCannotCommitBetweenItAndTheNotifications() {
        Payment payment = payuPayment(PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        candidates(payment);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "FAILED", "declined", new BigDecimal("150.0"), "INR"));

        paymentStatusPollingService.pollPendingGatewayPayments();

        verify(paymentRepository).findByIdForUpdate(payment.getInternalPaymentId());
        verify(paymentRepository, never()).findById(payment.getInternalPaymentId());
    }

    @Test
    void gatewayChecksNeverExceedTheConfiguredConcurrency() {
        paymentPollingProperties.setConcurrency(2);
        List<Payment> page = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            page.add(payuPayment("pay-" + i, PaymentStatus.INITIATED, LocalDateTime.now().minusMinutes(5)));
        }
        when(paymentRepository.findPollCandidatesAfter(any(), any(), eq(""), anyInt())).thenReturn(page);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        // The first two checks wait for each other, which proves two really run at once; the peak proves no third joins.
        CountDownLatch firstTwo = new CountDownLatch(2);
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenAnswer(inv -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            firstTwo.countDown();
            firstTwo.await(5, TimeUnit.SECONDS);
            inFlight.decrementAndGet();
            return new GatewayStatusResult("PAYU", "order-1", null, "PENDING", "in_progress", new BigDecimal("150.0"), "INR");
        });

        paymentStatusPollingService.pollPendingGatewayPayments();

        verify(statusCheckGatewayClient, times(8)).fetchStatus(any(Payment.class));
        assertThat(peak.get()).isEqualTo(2);
    }
}
