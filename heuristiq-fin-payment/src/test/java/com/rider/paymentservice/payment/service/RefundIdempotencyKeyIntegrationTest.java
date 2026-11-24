package com.heuristiq.paymentservice.payment.service;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.payment.dto.admin.AdminRefundRequest;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.model.RefundStatus;
import com.heuristiq.paymentservice.payment.repository.PaymentRefundRepository;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import com.heuristiq.paymentservice.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** Idempotency-Key header on the admin refund: replay, mismatch, absent key and concurrent duplicates. */
@SpringBootTest
@ActiveProfiles("test")
class RefundIdempotencyKeyIntegrationTest {

    @Autowired
    private AdminPaymentService adminPaymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentRefundRepository refundRepository;
    @MockBean
    private PaymentGatewayClientResolver gatewayClientResolver;
    @MockBean
    private com.heuristiq.paymentservice.refundaccounting.application.RefundAccountingService refundAccounting;

    private final AtomicInteger gatewayCalls = new AtomicInteger();
    private final java.util.Set<String> gatewayReferences = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        gatewayCalls.set(0);
        gatewayReferences.clear();
        RefundGatewayClient gateway = mock(RefundGatewayClient.class);
        reset(gatewayClientResolver);
        when(gatewayClientResolver.resolveRefund(any())).thenReturn(gateway);
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString())).thenAnswer(inv -> {
            gatewayCalls.incrementAndGet();
            gatewayReferences.add(inv.getArgument(3));
            return new GatewayRefundResult("rfnd_x", "processed");
        });
    }

    private Payment savePayment(BigDecimal amount) {
        String id = UUID.randomUUID().toString();
        return paymentRepository.save(Payment.builder()
                .internalPaymentId(id).rideId("ride-" + id).customerId("cust-" + id).amount(amount).currency("INR")
                .method(PaymentMethod.RAZORPAY).gateway(PaymentGatewayType.RAZORPAY).providerPaymentId("pay_" + id)
                .status(PaymentStatus.CAPTURED).idempotencyKey("idem-" + id)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private static AdminRefundRequest req(BigDecimal amount) {
        return new AdminRefundRequest(com.heuristiq.paymentservice.shared.money.Money.ofNullable(amount, "INR"), "r", "admin-1");
    }

    private BigDecimal committed(String paymentId) {
        return refundRepository.sumAmount(paymentId, EnumSet.of(RefundStatus.PROCESSING, RefundStatus.COMPLETED));
    }

    @Test
    void sameKeySamePayment_returnsOriginalResult_withoutSecondRefund() {
        Payment p = savePayment(new BigDecimal("100.0"));
        String key = "key-" + UUID.randomUUID();

        var first = adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("40.0")), key);
        var replay = adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("40.0")), key);

        assertThat(first.status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(replay.status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(committed(p.getInternalPaymentId())).isEqualByComparingTo("40.0");
        assertThat(gatewayCalls.get()).isEqualTo(1);
        assertThat(refundRepository.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    void replayAfterTheFirstRefundMadeThePaymentFullyRefunded_stillReturnsOriginalResult() {
        Payment p = savePayment(new BigDecimal("100.0"));
        String key = "key-" + UUID.randomUUID();

        var first = adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("100.0")), key);
        var replay = adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("100.0")), key);

        assertThat(first.status()).isEqualTo("REFUNDED");
        assertThat(replay.status()).isEqualTo("REFUNDED");
        assertThat(gatewayCalls.get()).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentAmount_isAConflict_andNothingExtraIsRefunded() {
        Payment p = savePayment(new BigDecimal("100.0"));
        String key = "key-" + UUID.randomUUID();
        adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("40.0")), key);

        assertThatThrownBy(() -> adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("30.0")), key))
                .isInstanceOf(ConflictException.class);
        assertThat(committed(p.getInternalPaymentId())).isEqualByComparingTo("40.0");
        assertThat(gatewayCalls.get()).isEqualTo(1);
    }

    @Test
    void sameKeyOnAnotherPayment_isAConflict() {
        Payment a = savePayment(new BigDecimal("100.0"));
        Payment b = savePayment(new BigDecimal("100.0"));
        String key = "key-" + UUID.randomUUID();
        adminPaymentService.refund(a.getInternalPaymentId(), req(new BigDecimal("40.0")), key);

        assertThatThrownBy(() -> adminPaymentService.refund(b.getInternalPaymentId(), req(new BigDecimal("40.0")), key))
                .isInstanceOf(ConflictException.class);
        assertThat(committed(b.getInternalPaymentId())).isZero();
    }

    @Test
    void missingKey_keepsExistingBehaviour_eachCallRefundsAgain() {
        Payment p = savePayment(new BigDecimal("100.0"));

        adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("30.0")), null);
        adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("30.0")));
        adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("30.0")), "  ");

        assertThat(committed(p.getInternalPaymentId())).isEqualByComparingTo("90.0");
        assertThat(gatewayCalls.get()).isEqualTo(3);
    }

    @Test
    void concurrentDuplicatesWithTheSameKey_refundExactlyOnce() throws Exception {
        Payment p = savePayment(new BigDecimal("100.0"));
        String key = "key-" + UUID.randomUUID();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<String> task = () -> {
                start.await();
                return adminPaymentService.refund(p.getInternalPaymentId(), req(new BigDecimal("25.0")), key).status();
            };
            futures.add(pool.submit(task));
        }
        start.countDown();
        for (Future<String> f : futures) {
            assertThat(f.get()).isEqualTo("PARTIALLY_REFUNDED");
        }
        pool.shutdown();

        // One ledger row, one refund reference. A replay that finds the first attempt still PROCESSING resumes
        // it (deliberate: that is how an unknown gateway outcome is recovered), so the gateway may be called
        // more than once, but always with the same refund reference, which the gateway dedupes on.
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtAsc(p.getInternalPaymentId())).hasSize(1);
        assertThat(committed(p.getInternalPaymentId())).isEqualByComparingTo("25.0");
        assertThat(gatewayReferences).hasSize(1);
    }
}
