package com.heuristiq.payment.payment.service;

import java.math.BigDecimal;
import com.heuristiq.payment.payment.dto.admin.AdminRefundRequest;
import com.heuristiq.payment.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** The admin refund endpoint contract on top of the refund ledger, and the summary's refunded total. */
@SpringBootTest
@ActiveProfiles("test")
class AdminPaymentRefundIntegrationTest {

    @Autowired
    private AdminPaymentService adminPaymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @MockBean
    private PaymentGatewayClientResolver gatewayClientResolver;
    @MockBean
    private com.heuristiq.payment.refundaccounting.application.RefundAccountingService refundAccounting;
    @MockBean
    private com.heuristiq.payment.wallet.port.WalletRefundCreditClient walletRefundCreditClient;

    @BeforeEach
    void setUp() {
        RefundGatewayClient gateway = mock(RefundGatewayClient.class);
        reset(gatewayClientResolver);
        when(gatewayClientResolver.resolveRefund(any())).thenReturn(gateway);
        when(gateway.refund(any(Payment.class), any(BigDecimal.class), any(), anyString()))
                .thenAnswer(inv -> new GatewayRefundResult("rfnd_x", "processed"));
    }

    private Payment savePayment(BigDecimal amount) {
        String id = UUID.randomUUID().toString();
        return paymentRepository.save(Payment.builder()
                .internalPaymentId(id).rideId("ride-" + id).customerId("cust-" + id).amount(amount).currency("INR")
                .method(PaymentMethod.RAZORPAY).gateway(PaymentGatewayType.RAZORPAY).providerPaymentId("pay_" + id)
                .status(PaymentStatus.CAPTURED).idempotencyKey("idem-" + id)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    @Test
    void oldContract_amountReasonAdminUser_stillWorks_andPartialRefundCanBeRepeated() {
        Payment payment = savePayment(new BigDecimal("100.0"));

        var first = adminPaymentService.refund(payment.getInternalPaymentId(), new AdminRefundRequest(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("40.0"), "INR"), "r", "admin-1"));
        var second = adminPaymentService.refund(payment.getInternalPaymentId(), new AdminRefundRequest(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("60.0"), "INR"), "r", "admin-1"));

        assertThat(first.status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(second.status()).isEqualTo("REFUNDED");
    }

    @Test
    void overRefundOfTheRemainingAmount_isAConflict() {
        Payment payment = savePayment(new BigDecimal("100.0"));
        adminPaymentService.refund(payment.getInternalPaymentId(), new AdminRefundRequest(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("70.0"), "INR"), "r", "admin-1"));

        assertThatThrownBy(() -> adminPaymentService.refund(payment.getInternalPaymentId(), new AdminRefundRequest(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("40.0"), "INR"), "r", "admin-1")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void newOptionalFields_destinationAndReference_areHonoured() {
        Payment payment = savePayment(new BigDecimal("100.0"));
        AdminRefundRequest request = new AdminRefundRequest(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("30.0"), "INR"), "r", "admin-1", "WALLET", "adm-ref-" + payment.getInternalPaymentId());

        adminPaymentService.refund(payment.getInternalPaymentId(), request);
        adminPaymentService.refund(payment.getInternalPaymentId(), request);

        assertThat(adminPaymentService.get(payment.getInternalPaymentId()).status()).isEqualTo("PARTIALLY_REFUNDED");
    }

    @Test
    void summary_runsAgainstTheRefundLedger() {
        Payment payment = savePayment(new BigDecimal("100.0"));
        adminPaymentService.refund(payment.getInternalPaymentId(), new AdminRefundRequest(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("25.0"), "INR"), "r", "admin-1"));

        var summary = adminPaymentService.summary(null, null);

        assertThat(summary.refundedAmount().value()).isGreaterThanOrEqualTo(new BigDecimal("25.00"));
    }
}
