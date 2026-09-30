package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.kiosk.dto.KioskDtos.CounterOrderLine;
import com.heuristq.dinein.kiosk.dto.KioskDtos.CounterOrderView;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderItemEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderSource;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.OfflinePaymentService;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The counter side of pay-at-counter: cashiers and waiters see the kiosk orders still waiting for money and settle
 * them. Only kiosk orders can be settled through here, so this is not a back door to pay arbitrary orders.
 */
@Service
public class KioskCounterService {

    /** EXPIRED orders stay payable (the customer may simply have queued a while), so they are listed too. */
    private static final Set<OrderStatus> UNPAID = EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED);
    private static final Duration LOOKBACK = Duration.ofHours(12);

    private final OrderRepository orderRepository;
    private final OfflinePaymentService offlinePaymentService;
    private final Clock clock;

    public KioskCounterService(OrderRepository orderRepository, OfflinePaymentService offlinePaymentService,
                               Clock clock) {
        this.orderRepository = orderRepository;
        this.offlinePaymentService = offlinePaymentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CounterOrderView> pending() {
        Long restaurantId = CurrentStaff.require().restaurantId();
        return orderRepository
                .findByRestaurantIdAndSourceAndStatusInAndPlacedAtGreaterThanEqualOrderByPlacedAtAsc(
                        restaurantId, OrderSource.KIOSK, UNPAID, clock.instant().minus(LOOKBACK))
                .stream().map(KioskCounterService::toView).toList();
    }

    /** Records the payment and confirms the order, which is what sends it to the kitchen. */
    @Transactional
    public void pay(Long orderId, OfflinePaymentMethod method) {
        StaffPrincipal staff = CurrentStaff.require();
        OrderEntity order = orderRepository.findByIdAndRestaurantId(orderId, staff.restaurantId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getSource() != OrderSource.KIOSK) {
            throw ApiException.conflict("NOT_A_KIOSK_ORDER", "Only kiosk orders can be settled here");
        }
        offlinePaymentService.recordAndConfirm(orderId, method, staff.userId(), "user:" + staff.userId());
    }

    private static CounterOrderView toView(OrderEntity o) {
        List<CounterOrderLine> lines = o.getItems().stream().map(KioskCounterService::toLine).toList();
        return new CounterOrderView(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getOrderType(),
                o.getStatus(), o.getGrandTotal(), o.getPlacedAt(), lines);
    }

    private static CounterOrderLine toLine(OrderItemEntity i) {
        return new CounterOrderLine(i.getItemName(), i.getVariantName(), i.getQuantity(), i.getNotes(),
                i.getAddons().stream().map(a -> a.getAddonName()).toList());
    }
}
