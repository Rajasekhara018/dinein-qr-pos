package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationRequest.PushTarget;
import com.heuristq.dinein.notification.NotificationRequest.Recipients;
import com.heuristq.dinein.order.OrderStatusChangedEvent;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.payment.PaymentFlaggedEvent;
import com.heuristq.dinein.payment.RefundFailedEvent;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns domain events into notifications. Listeners run after the business transaction commits, on the
 * notification executor, so recipient lookups and provider calls add no latency to the request that caused them.
 *
 * <ul>
 *   <li>Order CONFIRMED: IN_APP to owners + managers (and PUSH to their devices).</li>
 *   <li>Order READY: SMS to the guest's phone, PUSH to the guest's devices, IN_APP to waiters.</li>
 *   <li>Order CANCELLED: SMS to the guest's phone.</li>
 *   <li>Payment flagged: IN_APP (HIGH) to owners + managers (and their devices), EMAIL to active owners.</li>
 *   <li>Refund failed: IN_APP (HIGH) to owners (and their devices), EMAIL to active owners.</li>
 * </ul>
 */
@Slf4j
@Component
public class NotificationTriggers {

    private static final Set<StaffRole> OWNER_AND_MANAGER = EnumSet.of(StaffRole.OWNER, StaffRole.MANAGER);
    private static final Set<StaffRole> OWNER = EnumSet.of(StaffRole.OWNER);
    private static final Set<StaffRole> WAITER = EnumSet.of(StaffRole.WAITER);

    private final OrderRepository orderRepository;
    private final DiningTableRepository tableRepository;
    private final StaffUserRepository staffUserRepository;
    private final PushSubscriptionService pushSubscriptions;
    private final NotificationDispatcher dispatcher;

    public NotificationTriggers(OrderRepository orderRepository, DiningTableRepository tableRepository,
                                StaffUserRepository staffUserRepository, PushSubscriptionService pushSubscriptions,
                                NotificationDispatcher dispatcher) {
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.staffUserRepository = staffUserRepository;
        this.pushSubscriptions = pushSubscriptions;
        this.dispatcher = dispatcher;
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try {
            switch (event.to()) {
                case CONFIRMED -> withOrder(event.orderId(), (order, data) -> dispatcher.dispatch(new NotificationRequest(
                        NotificationEvent.ORDER_CONFIRMED, order.getId(), order.getRestaurantId(), data,
                        new Recipients(OWNER_AND_MANAGER, null, null,
                                staffPush(order.getRestaurantId(), OWNER_AND_MANAGER)))));
                case READY -> withOrder(event.orderId(), (order, data) -> dispatcher.dispatch(new NotificationRequest(
                        NotificationEvent.ORDER_READY, order.getId(), order.getRestaurantId(), data,
                        new Recipients(WAITER, null, guestPhone(order), guestPush(order)))));
                case CANCELLED -> withOrder(event.orderId(), (order, data) -> {
                    List<String> phones = guestPhone(order);
                    if (!phones.isEmpty()) {
                        dispatcher.dispatch(new NotificationRequest(NotificationEvent.ORDER_CANCELLED, order.getId(),
                                order.getRestaurantId(), data, new Recipients(null, null, phones, null)));
                    }
                });
                default -> {
                    // No notification for other transitions.
                }
            }
        } catch (RuntimeException e) {
            log.error("notification.trigger_failed event=order_status orderId={} to={}", event.orderId(), event.to(), e);
        }
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentFlagged(PaymentFlaggedEvent event) {
        try {
            withOrder(event.orderId(), (order, data) -> {
                data.put("reason", event.reason() == null ? "unknown reason" : event.reason());
                dispatcher.dispatch(new NotificationRequest(NotificationEvent.PAYMENT_FLAGGED, order.getId(),
                        order.getRestaurantId(), data,
                        new Recipients(OWNER_AND_MANAGER, ownerEmails(order.getRestaurantId()), null,
                                staffPush(order.getRestaurantId(), OWNER_AND_MANAGER))));
            });
        } catch (RuntimeException e) {
            log.error("notification.trigger_failed event=payment_flagged orderId={}", event.orderId(), e);
        }
    }

    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefundFailed(RefundFailedEvent event) {
        try {
            withOrder(event.orderId(), (order, data) -> dispatcher.dispatch(new NotificationRequest(
                    NotificationEvent.REFUND_FAILED, order.getId(), order.getRestaurantId(), data,
                    new Recipients(OWNER, ownerEmails(order.getRestaurantId()), null,
                            staffPush(order.getRestaurantId(), OWNER)))));
        } catch (RuntimeException e) {
            log.error("notification.trigger_failed event=refund_failed orderId={}", event.orderId(), e);
        }
    }

    private interface OrderAction {
        void run(OrderEntity order, Map<String, String> data);
    }

    private void withOrder(Long orderId, OrderAction action) {
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("notification.trigger_skipped reason=order_not_found orderId={}", orderId);
            return;
        }
        Map<String, String> data = new HashMap<>();
        data.put("orderId", String.valueOf(order.getId()));
        data.put("orderNumber", order.getOrderNumber());
        data.put("token", String.valueOf(order.getDisplayToken()));
        data.put("table", order.getTableId() == null ? "-"
                : tableRepository.findById(order.getTableId()).map(DiningTableEntity::getLabel).orElse("-"));
        action.run(order, data);
    }

    private List<PushTarget> guestPush(OrderEntity order) {
        // Staff-assisted orders have no guest session, hence no guest devices.
        return order.getGuestSessionId() == null ? List.of() : pushSubscriptions.guestTargets(order.getGuestSessionId());
    }

    private static List<String> guestPhone(OrderEntity order) {
        String phone = order.getCustomerPhone();
        return phone == null || phone.isBlank() ? List.of() : List.of(phone);
    }

    private List<StaffUserEntity> activeStaff(Long restaurantId, Set<StaffRole> roles) {
        // One restaurant's worth of staff rows, so filtering in memory is fine.
        return staffUserRepository.findAllByRestaurantIdOrderByUsernameAsc(restaurantId).stream()
                .filter(u -> u.isActive() && roles.contains(u.getRole()))
                .toList();
    }

    private List<String> ownerEmails(Long restaurantId) {
        return activeStaff(restaurantId, OWNER).stream().map(StaffUserEntity::getEmail)
                .filter(e -> e != null && !e.isBlank()).distinct().toList();
    }

    private List<PushTarget> staffPush(Long restaurantId, Set<StaffRole> roles) {
        return pushSubscriptions.staffTargets(activeStaff(restaurantId, roles).stream().map(StaffUserEntity::getId).toList());
    }
}
