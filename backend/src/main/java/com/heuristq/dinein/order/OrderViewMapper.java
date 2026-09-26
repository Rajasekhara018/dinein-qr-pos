package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderItemAddonEntity;
import com.heuristq.dinein.order.domain.OrderItemEntity;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.AddonView;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.BillView;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenLineView;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.OrderLineView;
import com.heuristq.dinein.order.dto.OrderDtos.PaymentView;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OrderViewMapper {

    private final DiningTableRepository tableRepository;

    public OrderViewMapper(DiningTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    /** Table labels for a batch of orders (tables are few; one query). */
    public Map<Long, String> tableLabels(Collection<OrderEntity> orders) {
        List<Long> ids = orders.stream().map(OrderEntity::getTableId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tableRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DiningTableEntity::getId, DiningTableEntity::getLabel));
    }

    public String tableLabel(OrderEntity order) {
        return order.getTableId() == null ? null
                : tableRepository.findById(order.getTableId()).map(DiningTableEntity::getLabel).orElse(null);
    }

    public GuestOrderView toGuestView(OrderEntity o, PaymentEntity payment, String tableLabel) {
        return new GuestOrderView(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(), tableLabel,
                o.getCustomerName(), o.getNotes(), lines(o), bill(o), payment == null ? null : paymentView(payment),
                o.getStatus() == OrderStatus.PENDING_PAYMENT && !o.isPaymentFlagged(),
                o.getPlacedAt(), o.getPaidAt(), o.getPreparingAt(), o.getReadyAt(), o.getCompletedAt(),
                o.getCancelledAt());
    }

    public GuestOrderSummary toGuestSummary(OrderEntity o) {
        return new GuestOrderSummary(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(),
                o.getGrandTotal(), itemCount(o), o.getPlacedAt());
    }

    public KitchenOrderView toKitchenView(OrderEntity o, String tableLabel) {
        List<KitchenLineView> lines = o.getItems().stream()
                .map(i -> new KitchenLineView(i.getItemName(), i.getVariantName(), i.getFoodType(),
                        i.getAddons().stream().map(OrderItemAddonEntity::getAddonName).toList(),
                        i.getQuantity(), i.getNotes()))
                .toList();
        return new KitchenOrderView(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(), tableLabel,
                o.getNotes(), lines, o.getPaidAt(), o.getPreparingAt(), o.getReadyAt());
    }

    public AdminOrderSummary toAdminSummary(OrderEntity o, String tableLabel, PaymentEntity payment) {
        return new AdminOrderSummary(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(), tableLabel,
                o.getCustomerName(), o.getGrandTotal(), itemCount(o), payment == null ? null : payment.getMethod(),
                o.isPaymentFlagged(), o.getPlacedAt(), o.getPaidAt());
    }

    public AdminOrderView toAdminView(OrderEntity o, List<PaymentEntity> payments, String tableLabel) {
        return new AdminOrderView(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(), tableLabel,
                o.getCustomerName(), o.getCustomerPhone(), o.getNotes(), lines(o), bill(o),
                payments.stream().sorted(Comparator.comparing(PaymentEntity::getId)).map(this::paymentView).toList(),
                o.isPaymentFlagged(), o.getFlagReason(), o.getCancelReason(), o.getPlacedAt(), o.getPaidAt(),
                o.getPreparingAt(), o.getReadyAt(), o.getCompletedAt(), o.getCancelledAt());
    }

    public PaymentView paymentView(PaymentEntity p) {
        return new PaymentView(p.getProvider(), p.getStatus().name(), p.getMethod(), p.getProviderPaymentId(),
                p.getProviderOrderId(), p.getAmountPaise(), p.getFailureReason(),
                p.getRefundStatus() == null ? null : p.getRefundStatus().name(), p.getProviderRefundId(), p.getUpdatedAt());
    }

    public BillView bill(OrderEntity o) {
        BigDecimal[] split = PricingService.splitGst(o.getTaxTotal());
        return new BillView(o.getSubtotal(), o.getTaxTotal(), split[0], split[1], o.getGrandTotal(),
                o.isPricesIncludeGst());
    }

    private List<OrderLineView> lines(OrderEntity o) {
        return o.getItems().stream().map(this::line).toList();
    }

    private OrderLineView line(OrderItemEntity i) {
        return new OrderLineView(i.getItemId(), i.getVariantId(), i.getItemName(), i.getVariantName(), i.getFoodType(),
                i.getAddons().stream().map(a -> new AddonView(a.getAddonName(), a.getPrice())).toList(),
                i.getUnitPrice(), i.getQuantity(), i.getGstPercent(), i.getLineTotal(), i.getTaxAmount(), i.getNotes());
    }

    private static int itemCount(OrderEntity o) {
        return o.getItems().stream().mapToInt(OrderItemEntity::getQuantity).sum();
    }
}
