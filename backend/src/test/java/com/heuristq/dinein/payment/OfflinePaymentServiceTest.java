package com.heuristq.dinein.payment;

import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.PaymentStateService.CaptureOutcome;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflinePaymentServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentStateService state = mock(PaymentStateService.class);
    private final OfflinePaymentService service = new OfflinePaymentService(orders, payments, state);

    private OrderEntity order;

    @BeforeEach
    void setUp() {
        order = new OrderEntity();
        order.setId(3L);
        order.setGrandTotal(new BigDecimal("819.00"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orders.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
        when(payments.findByOrderIdOrderByIdAsc(3L)).thenReturn(List.of());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "EXPIRED", "PAYMENT_FAILED"})
    void recordsACapturedOfflinePaymentAndConfirmsThroughTheCapturePath(OrderStatus status) {
        order.setStatus(status);
        when(state.confirmCapture(eq("OFFLINE"), anyString(), isNull(), eq(81900L), eq("CASH"), eq("user:4")))
                .thenReturn(CaptureOutcome.CONFIRMED);

        service.recordAndConfirm(3L, OfflinePaymentMethod.CASH, 4L, "user:4");

        ArgumentCaptor<PaymentEntity> saved = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(payments).saveAndFlush(saved.capture());
        PaymentEntity p = saved.getValue();
        assertThat(p.getProvider()).isEqualTo(PaymentEntity.OFFLINE_PROVIDER);
        assertThat(p.isOffline()).isTrue();
        assertThat(p.getMethod()).isEqualTo("CASH");
        assertThat(p.getAmountPaise()).isEqualTo(81900L);
        assertThat(p.getRecordedByStaffId()).isEqualTo(4L);
        assertThat(p.getProviderOrderId()).startsWith("off_3_").hasSizeLessThanOrEqualTo(64);
        verify(state).confirmCapture("OFFLINE", p.getProviderOrderId(), null, 81900L, "CASH", "user:4");
    }

    @Test
    void refusesAnOrderThatIsAlreadyPaid() {
        PaymentEntity online = new PaymentEntity();
        online.setStatus(PaymentStatus.CAPTURED);
        when(payments.findByOrderIdOrderByIdAsc(3L)).thenReturn(List.of(online));

        assertCode(() -> service.recordAndConfirm(3L, OfflinePaymentMethod.CASH, 4L, "user:4"), "ALREADY_PAID");
        verify(payments, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "COMPLETED"})
    void refusesOrdersThatCanNoLongerBePaid(OrderStatus status) {
        order.setStatus(status);

        assertCode(() -> service.recordAndConfirm(3L, OfflinePaymentMethod.UPI_AT_COUNTER, 4L, "user:4"),
                "ORDER_NOT_PAYABLE");
        verify(state, never()).confirmCapture(anyString(), anyString(), any(), anyLong(), any(), any());
    }

    @Test
    void refusesFlaggedOrders() {
        order.setPaymentFlagged(true);

        assertCode(() -> service.recordAndConfirm(3L, OfflinePaymentMethod.CASH, 4L, "user:4"), "PAYMENT_FLAGGED");
    }

    @Test
    void rollsBackWhenTheCaptureDoesNotConfirm() {
        when(state.confirmCapture(anyString(), anyString(), any(), anyLong(), any(), any()))
                .thenReturn(CaptureOutcome.DUPLICATE_PAYMENT);

        assertThatThrownBy(() -> service.recordAndConfirm(3L, OfflinePaymentMethod.CASH, 4L, "user:4"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(code);
    }
}
