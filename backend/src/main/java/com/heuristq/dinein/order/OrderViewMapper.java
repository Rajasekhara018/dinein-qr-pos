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
import com.heuristq.dinein.payment.domain.RefundStatus;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OrderViewMapper {

    private final DiningTableRepository tableRepository;
    private final StaffUserRepository staffUserRepository;

    public OrderViewMapper(DiningTableRepository tableRepository, StaffUserRepository staffUserRepository) {
        this.tableRepository = tableRepository;
        this.staffUserRepository = staffUserRepository;
    }

    /**
     * Table labels for a batch of orders (tables are few; one query). Every caller looks a takeaway order's
     * (table-less) {@code null} id up in the result directly, so this must tolerate a {@code null} key --
     * {@code Map.of()} does not (it throws on {@code get(null)}), unlike {@link Collections#emptyMap()}.
     */
    public Map<Long, String> tableLabels(Collection<OrderEntity> orders) {
        List<Long> ids = orders.stream().map(OrderEntity::getTableId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return tableRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DiningTableEntity::getId, DiningTableEntity::getLabel));
    }

    public String tableLabel(OrderEntity order) {
        return order.getTableId() == null ? null
                : tableRepository.findById(order.getTableId()).map(DiningTableEntity::getLabel).orElse(null);
    }

    public GuestOrderView toGuestView(OrderEntity o, PaymentEntity payment, String tableLabel) {
        return new GuestOrderView(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(), o.getOrderType(),
                tableLabel, o.getCustomerName(), o.getNotes(), lines(o), bill(o),
                payment == null ? null : paymentView(payment),
                o.getStatus() == OrderStatus.PENDING_PAYMENT && !o.isPaymentFlagged(),
                o.getPlacedAt(), o.getPaidAt(), o.getPreparingAt(), o.getReadyAt(), o.getCompletedAt(),
                o.getCancelledAt());
    }

    public GuestOrderSummary toGuestSummary(OrderEntity o) {
        return new GuestOrderSummary(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(),
                o.getOrderType(), o.getGrandTotal(), itemCount(o), o.getPlacedAt());
    }

    public KitchenOrderView toKitchenView(OrderEntity o, String tableLabel) {
        List<KitchenLineView> lines = o.getItems().stream()
                .map(i -> new KitchenLineView(i.getItemName(), i.getVariantName(), i.getFoodType(),
                        i.getAddons().stream().map(OrderItemAddonEntity::getAddonName).toList(),
                        i.getQuantity(), i.getNotes()))
                .toList();
        return new KitchenOrderView(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(),
                o.getOrderType(), o.getTableId(), tableLabel, o.getNotes(), lines, o.isPlacedByStaff(),
                o.getPaidAt(), o.getPreparingAt(), o.getReadyAt());
    }

    public AdminOrderSummary toAdminSummary(OrderEntity o, String tableLabel, PaymentEntity payment) {
        return new AdminOrderSummary(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(),
                o.getOrderType(), tableLabel, o.getCustomerName(), o.getGrandTotal(), itemCount(o),
                payment == null ? null : payment.getProvider(), payment == null ? null : payment.getMethod(),
                o.isPaymentFlagged(), o.getPlacedByStaffId(), o.getPlacedAt(), o.getPaidAt());
    }

    public AdminOrderView toAdminView(OrderEntity o, List<PaymentEntity> payments, String tableLabel) {
        String placedBy = o.getPlacedByStaffId() == null ? null
                : staffUserRepository.findById(o.getPlacedByStaffId())
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()).orElse(null);
        boolean manualRefundDue = payments.stream().anyMatch(p -> p.getRefundStatus() == RefundStatus.MANUAL);
        return new AdminOrderView(o.getId(), o.getOrderNumber(), o.getDisplayToken(), o.getStatus(), o.getOrderType(),
                tableLabel, o.getCustomerName(), o.getCustomerPhone(), o.getNotes(), lines(o), bill(o),
                payments.stream().sorted(Comparator.comparing(PaymentEntity::getId)).map(this::paymentView).toList(),
                o.isPaymentFlagged(), o.getFlagReason(), o.getCancelReason(), o.getPlacedByStaffId(), placedBy,
                manualRefundDue, o.getPlacedAt(), o.getPaidAt(), o.getPreparingAt(), o.getReadyAt(),
                o.getCompletedAt(), o.getCancelledAt());
    }

    public PaymentView paymentView(PaymentEntity p) {
        return new PaymentView(p.getProvider(), p.getStatus().name(), p.getMethod(), p.getProviderPaymentId(),
                p.getProviderOrderId(), p.getAmountPaise(), p.getFailureReason(),
                p.getRefundStatus() == null ? null : p.getRefundStatus().name(), p.getProviderRefundId(),
                p.getRecordedByStaffId(), p.getUpdatedAt());
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
