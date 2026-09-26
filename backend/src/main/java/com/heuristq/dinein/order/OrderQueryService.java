package com.heuristq.dinein.order;

import com.heuristq.dinein.guest.GuestSession;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.BusinessTime;
import com.heuristq.dinein.shared.web.PageResponse;
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
    private final OrderViewMapper mapper;
    private final SettingsService settingsService;
    private final Clock clock;

    public OrderQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                             OrderViewMapper mapper, SettingsService settingsService, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.mapper = mapper;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GuestOrderView guestOrder(Long orderId, GuestSession guest) {
        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> o.getGuestSessionId().equals(guest.sessionId()))
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

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummary> adminOrders(List<OrderStatus> statuses, LocalDate date, String q, int page, int size) {
        Specification<OrderEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
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
