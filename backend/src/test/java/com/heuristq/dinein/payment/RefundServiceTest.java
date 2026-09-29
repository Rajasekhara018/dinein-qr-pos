package com.heuristq.dinein.payment;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.order.OrderLifecycleService;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.payment.domain.RefundStatus;
import com.heuristq.dinein.payment.infrastructure.gateway.PaymentGateway;
import com.heuristq.dinein.payment.infrastructure.gateway.PaymentGatewayRegistry;
import com.heuristq.dinein.payment.infrastructure.gateway.ProviderRefund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderLifecycleService lifecycle = mock(OrderLifecycleService.class);
    private final PaymentGatewayRegistry gateways = mock(PaymentGatewayRegistry.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditService auditService = mock(AuditService.class);
    private final RefundService service = new RefundService(orders, payments, lifecycle, gateways,
            new DirectTransactionTemplate(), events, auditService);

    private OrderEntity order;

    @BeforeEach
    void setUp() {
        order = new OrderEntity();
        order.setId(5L);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));
    }

    private PaymentEntity captured(String provider, String method) {
        PaymentEntity p = new PaymentEntity();
        p.setId(9L);
        p.setOrderId(5L);
        p.setProvider(provider);
        p.setProviderOrderId("po_1");
        p.setProviderPaymentId("pay_1");
        p.setAmountPaise(81900L);
        p.setStatus(PaymentStatus.CAPTURED);
        p.setMethod(method);
        when(payments.findByOrderIdOrderByIdAsc(5L)).thenReturn(List.of(p));
        when(payments.findById(9L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void offlinePaymentIsMarkedForManualRefundWithoutTouchingAnyGateway() {
        PaymentEntity cash = captured(PaymentEntity.OFFLINE_PROVIDER, "CASH");

        service.cancelAndRefund(5L, "guest left", "user:1");

        verify(lifecycle).transition(order, OrderStatus.CANCELLED, "user:1");
        assertThat(cash.getRefundStatus()).isEqualTo(RefundStatus.MANUAL);
        assertThat(cash.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verifyNoInteractions(gateways);
    }

    @Test
    void cancellingAgainDoesNotRepeatAManualRefund() {
        PaymentEntity cash = captured(PaymentEntity.OFFLINE_PROVIDER, "CASH");
        cash.setRefundStatus(RefundStatus.MANUAL);
        order.setStatus(OrderStatus.CANCELLED);

        service.cancelAndRefund(5L, null, "user:1");

        assertThat(cash.getRefundStatus()).isEqualTo(RefundStatus.MANUAL);
        verifyNoInteractions(gateways);
    }

    @Test
    void onlinePaymentIsRefundedThroughItsGateway() {
        captured("RAZORPAY", "upi");
        PaymentGateway razorpay = mock(PaymentGateway.class);
        when(gateways.get("RAZORPAY")).thenReturn(razorpay);
        when(razorpay.refund(anyString(), anyString(), anyLong(), anyString())).thenReturn(new ProviderRefund("rfnd_1", false));

        service.cancelAndRefund(5L, null, "user:1");

        verify(razorpay).refund(eq("po_1"), eq("pay_1"), eq(81900L), any());
        verify(gateways, never()).get(PaymentEntity.OFFLINE_PROVIDER);
    }
}
