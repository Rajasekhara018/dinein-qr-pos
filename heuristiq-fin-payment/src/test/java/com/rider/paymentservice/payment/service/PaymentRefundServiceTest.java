package com.heuristiq.paymentservice.payment.service;

import com.heuristiq.paymentservice.payment.dto.RefundRequest;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentRefund;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.model.RefundDestination;
import com.heuristiq.paymentservice.payment.model.RefundStatus;
import com.heuristiq.paymentservice.payment.repository.PaymentRefundRepository;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import com.heuristiq.paymentservice.shared.exception.ConflictException;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import com.heuristiq.paymentservice.shared.exception.RefundOutcomeUnknownException;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.paymentservice.wallet.model.WalletTransaction;
import com.heuristiq.paymentservice.wallet.port.WalletCreditRejectedException;
import com.heuristiq.paymentservice.wallet.port.WalletRefundCreditClient;
import com.heuristiq.paymentservice.wallet.port.WalletRefundCreditCommand;
import org.mockito.ArgumentCaptor;
import com.heuristiq.paymentservice.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

/**
 * Refund engine (PAY-03 / PAY-04-refund) against a real H2 database with a mocked gateway client:
 * one destination per refund, idempotent references, cumulative tracking, over-refund rejection,
 * gateway call outside any transaction, and concurrent requests that cannot both pass the check.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentRefundServiceTest {

    @Autowired
    private PaymentRefundService refundService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentRefundRepository refundRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @MockBean
    private PaymentGatewayClientResolver gatewayClientResolver;
    @MockBean
    private WalletRefundCreditClient walletRefundCreditClient;

    @MockBean
    private com.heuristiq.paymentservice.refundaccounting.application.RefundAccountingService refundAccounting;

    private RefundGatewayClient gateway;

    @BeforeEach
    void setUp() {
        gateway = mock(RefundGatewayClient.class);
        reset(gatewayClientResolver, walletRefundCreditClient);
        when(walletRefundCreditClient.credit(any())).thenReturn("wallet-journal-1");
        when(gatewayClientResolver.resolveRefund(any())).thenReturn(gateway);
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString()))
                .thenAnswer(inv -> new GatewayRefundResult("rfnd_" + inv.getArgument(3), "processed"));
    }

    private Payment savePayment(PaymentMethod method, PaymentStatus status, BigDecimal amount) {
        String id = UUID.randomUUID().toString();
        return paymentRepository.save(Payment.builder()
                .internalPaymentId(id).rideId("ride-" + id).customerId("cust-" + id).driverId("driver-1")
                .amount(amount).currency("INR").method(method)
                .gateway(method == PaymentMethod.CASH ? null : PaymentGatewayType.RAZORPAY)
                .providerPaymentId(method == PaymentMethod.CASH ? null : "pay_" + id)
                .status(status).idempotencyKey("idem-" + id)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    /** Refund credits booked in the legacy wallet_transactions table for the customer. */
    private BigDecimal refundCredits(String customerId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM wallet_transactions WHERE user_id = ?",
                BigDecimal.class, customerId);
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getInternalPaymentId()).orElseThrow();
    }

    // ── PAY-03: one destination per refund ──────────────────────────────────

    @Test
    void gatewayPayment_defaultsToTheGateway_andDoesNotAlsoCreditTheWallet() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("200.0"));

        var response = refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, "customer request"), "admin-1");

        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(refundAccounting).record(org.mockito.ArgumentMatchers.argThat(p ->
                p.eventId().equals("ride-completed:" + payment.getRideId())
                        && p.amount().compareTo(new BigDecimal("200")) == 0 && !p.creditAlreadyPosted()));
        verify(gateway, times(1)).refund(any(Payment.class), eq(new BigDecimal("200.00")), eq("customer request"), anyString());
        assertThat(refundCredits(payment.getCustomerId())).isZero();
        PaymentRefund refund = refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId()).get(0);
        assertThat(refund.getDestination()).isEqualTo(RefundDestination.GATEWAY);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getGatewayRefundId()).startsWith("rfnd_");
        assertThat(refund.getCreatedBy()).isEqualTo("admin-1");
        assertThat(refund.getAmount()).isEqualByComparingTo("200.00");
        assertThat(refund.getAmountCurrency()).isEqualTo("INR");   // the payment's currency
    }

    @Test
    void upiPaidToTheDriver_defaultsToTheWallet_andCannotGoBackThroughTheGateway() {
        Payment payment = paymentRepository.save(Payment.builder()
                .internalPaymentId(UUID.randomUUID().toString()).rideId("ride-upi").customerId("cust-upi").driverId("driver-1")
                .amount(new BigDecimal("80.0")).currency("INR").method(PaymentMethod.UPI_DIRECT)
                .status(PaymentStatus.CAPTURED).idempotencyKey("direct-collection:ride-upi")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null, "GATEWAY", null), "admin-1"))
                .isInstanceOf(com.heuristiq.paymentservice.shared.exception.PaymentServiceException.class);
        var response = refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "admin-1");

        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(gatewayClientResolver, never()).resolveRefund(any());
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId()).get(0).getDestination())
                .isEqualTo(RefundDestination.WALLET);
    }

    @Test
    void cashPayment_defaultsToTheWallet_andNeverCallsTheGateway() {
        Payment payment = savePayment(PaymentMethod.CASH, PaymentStatus.CAPTURED, new BigDecimal("50.0"));

        var response = refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "admin-1");

        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(gatewayClientResolver, never()).resolveRefund(any());
        PaymentRefund refund = refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId()).get(0);
        ArgumentCaptor<WalletRefundCreditCommand> sent = ArgumentCaptor.forClass(WalletRefundCreditCommand.class);
        verify(walletRefundCreditClient, times(1)).credit(sent.capture());
        assertThat(sent.getValue().refundId()).isEqualTo(refund.getId());
        assertThat(sent.getValue().paymentId()).isEqualTo(payment.getInternalPaymentId());
        assertThat(sent.getValue().userId()).isEqualTo(payment.getCustomerId());
        assertThat(sent.getValue().amount().value()).isEqualByComparingTo("50.00");
        assertThat(sent.getValue().amount().code()).isEqualTo("INR");
        assertThat(refund.getWalletJournalId()).isEqualTo("wallet-journal-1");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        // the legacy table is not written any more
        assertThat(refundCredits(payment.getCustomerId())).isZero();
    }

    @Test
    void cashPayment_cannotBeRefundedToTheGateway() {
        Payment payment = savePayment(PaymentMethod.CASH, PaymentStatus.CAPTURED, new BigDecimal("50.0"));

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(),
                new RefundRequest(null, null, "GATEWAY", null), "admin-1"))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("Cash payments cannot be refunded to the payment gateway");

        verify(gatewayClientResolver, never()).resolveRefund(any());
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId())).isEmpty();
    }

    @Test
    void gatewayPayment_canBeRefundedToTheWalletWhenExplicitlyAsked_withoutAGatewayCall() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("120.0"));

        refundService.refund(payment.getInternalPaymentId(), new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("120.0"), "INR"), "goodwill", "wallet", null), "admin-1");

        verify(gatewayClientResolver, never()).resolveRefund(any());
        verify(walletRefundCreditClient).credit(any());
        assertThat(refundCredits(payment.getCustomerId())).isZero();
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void gatewayRefund_neverCallsTheWallet() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("80.0"));

        refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "admin-1");

        verify(walletRefundCreditClient, never()).credit(any());
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId()).get(0).getWalletJournalId()).isNull();
    }

    @Test
    void walletCreditCarriesThePaymentsOrganization_andIsCalledOutsideAnyTransaction() {
        Payment payment = savePayment(PaymentMethod.CASH, PaymentStatus.CAPTURED, new BigDecimal("40.0"));
        payment.setTenantId("org-a");
        paymentRepository.save(payment);
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        AtomicBoolean markerVisible = new AtomicBoolean(false);
        when(walletRefundCreditClient.credit(any())).thenAnswer(inv -> {
            WalletRefundCreditCommand c = inv.getArgument(0);
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            markerVisible.set(refundRepository.findById(c.refundId()).map(r -> r.getStatus() == RefundStatus.PROCESSING).orElse(false));
            return "j-1";
        });

        refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "admin-1");

        ArgumentCaptor<WalletRefundCreditCommand> sent = ArgumentCaptor.forClass(WalletRefundCreditCommand.class);
        verify(walletRefundCreditClient).credit(sent.capture());
        assertThat(sent.getValue().tenantId()).isEqualTo("org-a");
        assertThat(transactionActive.get()).isFalse();
        assertThat(markerVisible.get()).isTrue();
    }

    @Test
    void walletUnavailable_leavesTheRefundProcessing_andTheSameReferenceResumesItWithoutDoubleCrediting() {
        Payment payment = savePayment(PaymentMethod.CASH, PaymentStatus.CAPTURED, new BigDecimal("60.0"));
        String id = payment.getInternalPaymentId();
        String ref = "wallet-retry-" + id;
        when(walletRefundCreditClient.credit(any())).thenThrow(
                new DownstreamUnavailableException("wallet-service", DownstreamUnavailableException.Reason.CIRCUIT_OPEN, null));

        assertThatThrownBy(() -> refundService.refund(id, new RefundRequest(null, null, null, ref), "a"))
                .isInstanceOf(DownstreamUnavailableException.class);

        PaymentRefund pending = refundRepository.findByRefundReference(ref).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        // the amount stays reserved: a second, different refund can not take it
        assertThatThrownBy(() -> refundService.refund(id, new RefundRequest(null, null), "a")).isInstanceOf(ConflictException.class);

        org.mockito.Mockito.doReturn("j-9").when(walletRefundCreditClient).credit(any());
        assertThat(refundService.refund(id, new RefundRequest(null, null, null, ref), "a").status()).isEqualTo("REFUNDED");

        PaymentRefund done = refundRepository.findByRefundReference(ref).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(done.getWalletJournalId()).isEqualTo("j-9");
        ArgumentCaptor<WalletRefundCreditCommand> sent = ArgumentCaptor.forClass(WalletRefundCreditCommand.class);
        verify(walletRefundCreditClient, times(2)).credit(sent.capture());
        assertThat(sent.getAllValues()).extracting(WalletRefundCreditCommand::refundId).containsOnly(pending.getId());
    }

    @Test
    void walletRefusal_marksTheRefundFailed_andReleasesTheAmount() {
        Payment payment = savePayment(PaymentMethod.CASH, PaymentStatus.CAPTURED, new BigDecimal("60.0"));
        String id = payment.getInternalPaymentId();
        String ref = "wallet-refused-" + id;
        when(walletRefundCreditClient.credit(any())).thenThrow(new WalletCreditRejectedException(409, "APP_NOT_ENABLED"));

        assertThatThrownBy(() -> refundService.refund(id, new RefundRequest(null, null, null, ref), "a"))
                .isInstanceOf(PaymentServiceException.class).hasMessageContaining("APP_NOT_ENABLED");

        PaymentRefund failed = refundRepository.findByRefundReference(ref).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("APP_NOT_ENABLED");
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThatThrownBy(() -> refundService.refund(id, new RefundRequest(null, null, null, ref), "a"))
                .isInstanceOf(ConflictException.class);
        org.mockito.Mockito.doReturn("j-2").when(walletRefundCreditClient).credit(any());
        assertThat(refundService.refund(id, new RefundRequest(null, null), "a").status()).isEqualTo("REFUNDED");
    }

    @Test
    void aCompletedWalletRefundReplayedWithTheSameReference_doesNotCallTheWalletAgain() {
        Payment payment = savePayment(PaymentMethod.CASH, PaymentStatus.CAPTURED, new BigDecimal("30.0"));
        String id = payment.getInternalPaymentId();
        String ref = "wallet-replay-" + id;
        refundService.refund(id, new RefundRequest(null, null, null, ref), "a");

        refundService.refund(id, new RefundRequest(null, null, null, ref), "a");

        verify(walletRefundCreditClient, times(1)).credit(any());
    }

    @Test
    void walletRefund_ofAPaymentWithoutACustomer_isRejectedBeforeAnythingIsReserved() {
        Payment payment = savePayment(PaymentMethod.CASH, PaymentStatus.CAPTURED, new BigDecimal("30.0"));
        payment.setCustomerId(null);
        paymentRepository.save(payment);

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a"))
                .isInstanceOf(PaymentServiceException.class).hasMessageContaining("no customer");

        verify(walletRefundCreditClient, never()).credit(any());
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId())).isEmpty();
    }

    @Test
    void invalidDestination_isRejected() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(),
                new RefundRequest(null, null, "CHEQUE", null), "admin-1"))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("Unsupported refund destination");
    }

    // ── Idempotency ──────────────────────────────────────────────────────────

    @Test
    void sameReference_returnsTheExistingRefund_andRefundsOnlyOnce() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("200.0"));
        RefundRequest request = new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("80.0"), "INR"), "r", null, "ref-" + payment.getInternalPaymentId());

        var first = refundService.refund(payment.getInternalPaymentId(), request, "admin-1");
        var second = refundService.refund(payment.getInternalPaymentId(), request, "admin-1");

        assertThat(second.status()).isEqualTo(first.status()).isEqualTo("PARTIALLY_REFUNDED");
        verify(gateway, times(1)).refund(any(Payment.class), any(BigDecimal.class), any(), anyString());
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId())).hasSize(1);
    }

    @Test
    void referenceReusedWithADifferentAmount_isAConflict() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("200.0"));
        String ref = "ref-" + payment.getInternalPaymentId();
        refundService.refund(payment.getInternalPaymentId(), new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("80.0"), "INR"), null, null, ref), "admin-1");

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(),
                new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("90.0"), "INR"), null, null, ref), "admin-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void referenceOfAnotherPayment_isAConflict() {
        Payment a = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("200.0"));
        Payment b = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("200.0"));
        String ref = "shared-ref-" + a.getInternalPaymentId();
        refundService.refund(a.getInternalPaymentId(), new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("10.0"), "INR"), null, null, ref), "admin-1");

        assertThatThrownBy(() -> refundService.refund(b.getInternalPaymentId(),
                new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("10.0"), "INR"), null, null, ref), "admin-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void gatewayCall_carriesTheRefundReference_soTheGatewayCanDeduplicate() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("200.0"));

        refundService.refund(payment.getInternalPaymentId(), new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("50.0"), "INR"), null, null, "my-ref-" + payment.getInternalPaymentId()), "admin-1");

        verify(gateway).refund(any(Payment.class), eq(new BigDecimal("50.00")), any(), eq("my-ref-" + payment.getInternalPaymentId()));
    }

    // ── Cumulative tracking, partial refunds, over-refund ───────────────────

    @Test
    void partialRefundsCanBeRepeated_upToTheRemainingAmount_thenOverRefundIsRejected() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));
        String id = payment.getInternalPaymentId();

        assertThat(refundService.refund(id, new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("30.0"), "INR"), null), "a").status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(refundService.refund(id, new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("30.0"), "INR"), null), "a").status()).isEqualTo("PARTIALLY_REFUNDED");

        assertThatThrownBy(() -> refundService.refund(id, new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("50.0"), "INR"), null), "a"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("remaining refundable amount of 40.0");

        assertThat(refundService.refund(id, new RefundRequest(null, null), "a").status()).isEqualTo("REFUNDED");
        assertThat(refundRepository.sumAmount(id, java.util.EnumSet.of(RefundStatus.COMPLETED))).isEqualByComparingTo("100.0");
        verify(gateway, times(3)).refund(any(Payment.class), any(BigDecimal.class), any(), anyString());
    }

    @Test
    void fullyRefundedPayment_cannotBeRefundedAgain_with409() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));
        refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a");

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a"))
                .isInstanceOf(ConflictException.class);
        verify(gateway, times(1)).refund(any(Payment.class), any(BigDecimal.class), any(), anyString());
    }

    @Test
    void amountAboveThePaymentAmount_isRejectedAs400() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("100.01"), "INR"), null), "a"))
                .isInstanceOf(PaymentServiceException.class)
                .isNotInstanceOf(ConflictException.class)
                .hasMessageContaining("exceeds the captured amount");
        verify(gatewayClientResolver, never()).resolveRefund(any());
    }

    @Test
    void paymentThatIsNotCaptured_cannotBeRefunded() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.INITIATED, new BigDecimal("100.0"));

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a"))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("current status: INITIATED");
    }

    @Test
    void legacyRefundCredits_stillLimitNewRefunds() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.PARTIALLY_REFUNDED, new BigDecimal("100.0"));
        walletTransactionRepository.save(WalletTransaction.builder()
                .id(UUID.randomUUID().toString()).userId(payment.getCustomerId()).amount(new BigDecimal("70.0")).type("CREDIT")
                .source("REFUND").referenceId(payment.getInternalPaymentId()).status("SUCCESS")
                .createdAt(LocalDateTime.now()).build());

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("40.0"), "INR"), null), "a"))
                .isInstanceOf(ConflictException.class);
        assertThat(refundService.refund(payment.getInternalPaymentId(), new RefundRequest(com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("30.0"), "INR"), null), "a").status())
                .isEqualTo("REFUNDED");
    }

    // ── Gateway outcomes ─────────────────────────────────────────────────────

    @Test
    void definiteGatewayRejection_marksTheRefundFailed_releasesTheAmount_andLeavesThePaymentUnchanged() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString()))
                .thenThrow(new PaymentServiceException("Razorpay refund failed (400 BAD_REQUEST): rejected"));

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a"))
                .isInstanceOf(PaymentServiceException.class).hasMessageContaining("Razorpay refund failed");

        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(refundCredits(payment.getCustomerId())).isZero();
        PaymentRefund refund = refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId()).get(0);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);

        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString()))
                .thenAnswer(inv -> new GatewayRefundResult("rfnd_ok", "processed"));
        assertThat(refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a").status())
                .isEqualTo("REFUNDED");
    }

    @Test
    void retryOfAFailedReference_isAConflict_askingForANewReference() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));
        String ref = "ref-failed-" + payment.getInternalPaymentId();
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString()))
                .thenThrow(new PaymentServiceException("rejected"));
        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null, null, ref), "a"))
                .isInstanceOf(PaymentServiceException.class);

        assertThatThrownBy(() -> refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null, null, ref), "a"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("new reference");
    }

    @Test
    void unknownGatewayOutcome_keepsTheAmountReserved_andTheSameReferenceResumesIt() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));
        String id = payment.getInternalPaymentId();
        String ref = "ref-timeout-" + id;
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString()))
                .thenThrow(new RefundOutcomeUnknownException("timed out"));

        assertThatThrownBy(() -> refundService.refund(id, new RefundRequest(null, null, null, ref), "a"))
                .isInstanceOf(RefundOutcomeUnknownException.class);

        assertThat(refundRepository.findByRefundReference(ref).orElseThrow().getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        // The amount stays reserved: a different reference cannot refund the same money again.
        assertThatThrownBy(() -> refundService.refund(id, new RefundRequest(null, null, null, "other-" + id), "a"))
                .isInstanceOf(ConflictException.class);

        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString()))
                .thenAnswer(inv -> new GatewayRefundResult("rfnd_after_retry", "processed"));
        assertThat(refundService.refund(id, new RefundRequest(null, null, null, ref), "a").status()).isEqualTo("REFUNDED");

        PaymentRefund refund = refundRepository.findByRefundReference(ref).orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getGatewayRefundId()).isEqualTo("rfnd_after_retry");
        assertThat(refundCredits(payment.getCustomerId())).isZero();
    }

    @Test
    void gatewayIsCalledOutsideAnyDatabaseTransaction_afterTheProcessingMarkerIsCommitted() {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        AtomicBoolean markerVisible = new AtomicBoolean(false);
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString())).thenAnswer(inv -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            markerVisible.set(refundRepository.findByRefundReference(inv.getArgument(3)).map(r -> r.getStatus() == RefundStatus.PROCESSING).orElse(false));
            return new GatewayRefundResult("rfnd_1", "processed");
        });

        refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a");

        assertThat(transactionActive.get()).isFalse();
        assertThat(markerVisible.get()).isTrue();
    }

    // ── Concurrency ──────────────────────────────────────────────────────────

    @Test
    void twoConcurrentFullRefunds_onlyOneReachesTheGateway() throws Exception {
        Payment payment = savePayment(PaymentMethod.RAZORPAY, PaymentStatus.CAPTURED, new BigDecimal("100.0"));
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString())).thenAnswer(inv -> {
            Thread.sleep(300);
            return new GatewayRefundResult("rfnd_slow", "processed");
        });

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Void> task = () -> {
                start.await();
                try {
                    refundService.refund(payment.getInternalPaymentId(), new RefundRequest(null, null), "a");
                    ok.incrementAndGet();
                } catch (ConflictException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            };
            futures.add(pool.submit(task));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        verify(gateway, times(1)).refund(any(Payment.class), any(BigDecimal.class), any(), anyString());
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getInternalPaymentId())).hasSize(1);
        assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
