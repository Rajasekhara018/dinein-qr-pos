package com.heuristq.dinein.order;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.order.PricingService.LineInput;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderItemAddonEntity;
import com.heuristq.dinein.order.domain.OrderItemEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderView;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Splits an unpaid order into two or more new orders (separate bills for a group at one table) by item. The
 * original order is cancelled (nothing to refund: it never reached a paid status) and each new order starts fresh
 * at PENDING_PAYMENT, going through the same checkout/mark-paid flow as any other order.
 */
@Slf4j
@Service
public class OrderSplitService {

    private final OrderRepository orderRepository;
    private final OrderNumberService orderNumberService;
    private final PricingService pricingService;
    private final SettingsService settingsService;
    private final OrderLifecycleService lifecycle;
    private final OrderViewMapper viewMapper;
    private final AuditService auditService;
    private final Clock clock;

    public OrderSplitService(OrderRepository orderRepository, OrderNumberService orderNumberService,
                             PricingService pricingService, SettingsService settingsService,
                             OrderLifecycleService lifecycle, OrderViewMapper viewMapper, AuditService auditService,
                             Clock clock) {
        this.orderRepository = orderRepository;
        this.orderNumberService = orderNumberService;
        this.pricingService = pricingService;
        this.settingsService = settingsService;
        this.lifecycle = lifecycle;
        this.viewMapper = viewMapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public List<AdminOrderView> split(Long orderId, List<List<Long>> itemGroups, String actor) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("NOT_SPLITTABLE",
                    "Only orders awaiting payment can be split (current: " + order.getStatus() + ")");
        }
        if (itemGroups.size() < 2) {
            throw ApiException.badRequest("SPLIT_REQUIRES_GROUPS", "Provide at least two groups of items");
        }
        Map<Long, OrderItemEntity> byId = order.getItems().stream()
                .collect(Collectors.toMap(OrderItemEntity::getId, Function.identity()));
        Set<Long> assigned = new HashSet<>();
        for (List<Long> group : itemGroups) {
            for (Long itemId : group) {
                if (!byId.containsKey(itemId)) {
                    throw ApiException.badRequest("UNKNOWN_ITEM", "Item " + itemId + " does not belong to this order");
                }
                if (!assigned.add(itemId)) {
                    throw ApiException.badRequest("ITEM_LISTED_TWICE", "Item " + itemId + " is listed in more than one group");
                }
            }
        }
        if (assigned.size() != byId.size()) {
            throw ApiException.badRequest("INCOMPLETE_SPLIT", "Every item on the order must be assigned to exactly one group");
        }

        RestaurantSettingsEntity settings = settingsService.forRestaurant(order.getRestaurantId());
        List<OrderEntity> created = new ArrayList<>();
        for (List<Long> group : itemGroups) {
            created.add(createSplitOrder(order, group.stream().map(byId::get).toList(), settings));
        }

        order.setCancelReason("Split into " + itemGroups.size() + " orders");
        lifecycle.transition(order, OrderStatus.CANCELLED, actor);
        log.info("order.split orderId={} into={} actor={}", orderId, itemGroups.size(), actor);
        auditService.record("ORDER_SPLIT", "Order", orderId, order.getOrderNumber(),
                created.stream().map(OrderEntity::getOrderNumber).collect(Collectors.joining(", ")));

        return created.stream().map(o -> viewMapper.toAdminView(o, List.of(), viewMapper.tableLabel(o))).toList();
    }

    private OrderEntity createSplitOrder(OrderEntity original, List<OrderItemEntity> items,
                                         RestaurantSettingsEntity settings) {
        PricingService.Bill bill = pricingService.calculate(
                items.stream().map(i -> new LineInput(i.getUnitPrice(), i.getQuantity(), i.getGstPercent())).toList(),
                settings.isPricesIncludeGst());

        OrderNumberService.Allocated number = orderNumberService.next();
        OrderEntity split = new OrderEntity();
        split.setRestaurantId(original.getRestaurantId());
        split.setOrderNumber(number.orderNumber());
        split.setDisplayToken(number.displayToken());
        split.setTableId(original.getTableId());
        split.setGuestSessionId(original.getGuestSessionId());
        split.setPlacedByStaffId(original.getPlacedByStaffId());
        split.setOrderType(original.getOrderType());
        split.setCustomerName(original.getCustomerName());
        split.setCustomerPhone(original.getCustomerPhone());
        split.setStatus(OrderStatus.PENDING_PAYMENT);
        split.setSubtotal(bill.subtotal());
        split.setTaxTotal(bill.taxTotal());
        split.setGrandTotal(bill.grandTotal());
        split.setPricesIncludeGst(settings.isPricesIncludeGst());
        split.setIdempotencyKey("split-" + original.getId() + "-" + number.orderNumber());
        split.setPlacedAt(clock.instant());

        for (int i = 0; i < items.size(); i++) {
            OrderItemEntity source = items.get(i);
            PricingService.LineResult priced = bill.lines().get(i);
            OrderItemEntity copy = new OrderItemEntity();
            copy.setItemId(source.getItemId());
            copy.setVariantId(source.getVariantId());
            copy.setItemName(source.getItemName());
            copy.setVariantName(source.getVariantName());
            copy.setFoodType(source.getFoodType());
            copy.setStationId(source.getStationId());
            copy.setStationName(source.getStationName());
            copy.setUnitPrice(source.getUnitPrice());
            copy.setQuantity(source.getQuantity());
            copy.setGstPercent(source.getGstPercent());
            copy.setLineTotal(priced.lineTotal());
            copy.setTaxAmount(priced.taxAmount());
            copy.setNotes(source.getNotes());
            for (OrderItemAddonEntity sourceAddon : source.getAddons()) {
                OrderItemAddonEntity addonCopy = new OrderItemAddonEntity();
                addonCopy.setAddonId(sourceAddon.getAddonId());
                addonCopy.setAddonName(sourceAddon.getAddonName());
                addonCopy.setPrice(sourceAddon.getPrice());
                copy.addAddon(addonCopy);
            }
            split.addItem(copy);
        }
        orderRepository.saveAndFlush(split);
        return split;
    }
}
