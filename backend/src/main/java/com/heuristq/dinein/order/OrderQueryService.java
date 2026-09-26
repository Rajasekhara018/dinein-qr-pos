package com.heuristq.dinein.order;

import com.heuristq.dinein.guest.GuestSession;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.WaiterTableView;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.BusinessTime;
import com.heuristq.dinein.shared.web.PageResponse;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DiningTableRepository tableRepository;
    private final OrderViewMapper mapper;
    private final SettingsService settingsService;
    private final Clock clock;

    public OrderQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                             DiningTableRepository tableRepository, OrderViewMapper mapper,
                             SettingsService settingsService, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.tableRepository = tableRepository;
        this.mapper = mapper;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GuestOrderView guestOrder(Long orderId, GuestSession guest) {
        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> o.belongsToGuest(guest.sessionId()))
                .orElseThrow(() -> ApiException.notFound("Order"));
        PaymentEntity latest = paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .max(Comparator.comparing(PaymentEntity::getId)).orElse(null);
        return mapper.toGuestView(order, latest, mapper.tableLabel(order));
    }

    @Transactional(readOnly = true)
    public List<GuestOrderSummary> guestOrders(GuestSession guest) {
        return orderRepository.findByGuestSessionIdOrderByPlacedAtDesc(guest.sessionId()).stream()
                .map(mapper::toGuestSummary).toList();
    }

    /** Active kitchen orders, oldest paid first. READY orders drop off after the configured auto-hide window. */
    @Transactional(readOnly = true)
    public List<KitchenOrderView> kitchenOrders(Collection<OrderStatus> statuses) {
        List<OrderStatus> wanted = statuses.stream().filter(OrderStatus.KITCHEN_VISIBLE::contains).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        Instant hideBefore = clock.instant().minus(Duration.ofMinutes(settingsService.current().getReadyAutoHideMinutes()));
        List<OrderEntity> orders = orderRepository.findByStatusInOrderByPaidAtAsc(wanted).stream()
                .filter(o -> o.getStatus() != OrderStatus.READY || o.getReadyAt() == null || o.getReadyAt().isAfter(hideBefore))
                .toList();
        Map<Long, String> labels = mapper.tableLabels(orders);
        return orders.stream().map(o -> mapper.toKitchenView(o, labels.get(o.getTableId()))).toList();
    }

    /**
     * Active orders for the waiter screen (CONFIRMED, PREPARING, READY), oldest paid first. Unlike the kitchen view,
     * READY orders never auto-hide here: they stay until a waiter marks them served.
     */
    @Transactional(readOnly = true)
    public List<KitchenOrderView> waiterOrders(Collection<OrderStatus> statuses) {
        List<OrderStatus> wanted = statuses.stream().filter(OrderStatus.KITCHEN_VISIBLE::contains).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<OrderEntity> orders = orderRepository.findByStatusInOrderByPaidAtAsc(wanted);
        Map<Long, String> labels = mapper.tableLabels(orders);
        return orders.stream().map(o -> mapper.toKitchenView(o, labels.get(o.getTableId()))).toList();
    }

    /** Active tables with counts of their open (paid, not yet served) orders. */
    @Transactional(readOnly = true)
    public List<WaiterTableView> waiterTables() {
        Map<Long, List<OrderEntity>> open = orderRepository.findByStatusInOrderByPaidAtAsc(OrderStatus.KITCHEN_VISIBLE)
                .stream().filter(o -> o.getTableId() != null)
                .collect(Collectors.groupingBy(OrderEntity::getTableId));
        return tableRepository.findAllByOrderByLabelAsc().stream()
                .filter(DiningTableEntity::isActive)
                .map(t -> {
                    List<OrderEntity> orders = open.getOrDefault(t.getId(), List.of());
                    return new WaiterTableView(t.getId(), t.getLabel(), orders.size(),
                            count(orders, OrderStatus.CONFIRMED), count(orders, OrderStatus.PREPARING),
                            count(orders, OrderStatus.READY),
                            orders.stream().map(o -> o.getPaidAt() != null ? o.getPaidAt() : o.getPlacedAt())
                                    .min(Comparator.naturalOrder()).orElse(null));
                })
                .toList();
    }

    /** Any order with its latest payment, in the guest-order shape (used by the waiter's order page). */
    @Transactional(readOnly = true)
    public GuestOrderView staffOrder(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        PaymentEntity latest = paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .max(Comparator.comparing(PaymentEntity::getId)).orElse(null);
        return mapper.toGuestView(order, latest, mapper.tableLabel(order));
    }

    private static int count(List<OrderEntity> orders, OrderStatus status) {
        return (int) orders.stream().filter(o -> o.getStatus() == status).count();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummary> adminOrders(List<OrderStatus> statuses, OrderType orderType, LocalDate date,
                                                       String q, int page, int size) {
        Specification<OrderEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            if (orderType != null) {
                p.add(cb.equal(root.get("orderType"), orderType));
            }
            if (date != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("placedAt"), BusinessTime.startOfDay(date)));
                p.add(cb.lessThan(root.get("placedAt"), BusinessTime.startOfDay(date.plusDays(1))));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("%", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("orderNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("customerName"), "")), like),
                        cb.like(cb.coalesce(root.get("customerPhone"), ""), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<OrderEntity> result = orderRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "placedAt")));
        Map<Long, String> labels = mapper.tableLabels(result.getContent());
        Map<Long, PaymentEntity> payments = latestPayments(result.getContent().stream().map(OrderEntity::getId).toList());
        return PageResponse.of(result, o -> mapper.toAdminSummary(o, labels.get(o.getTableId()), payments.get(o.getId())));
    }

    @Transactional(readOnly = true)
    public AdminOrderView adminOrder(Long id) {
        OrderEntity order = orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Order"));
        return mapper.toAdminView(order, paymentRepository.findByOrderIdOrderByIdAsc(id), mapper.tableLabel(order));
    }

    private Map<Long, PaymentEntity> latestPayments(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return paymentRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(PaymentEntity::getOrderId, p -> p,
                        (a, b) -> a.getId() > b.getId() ? a : b));
    }
}
