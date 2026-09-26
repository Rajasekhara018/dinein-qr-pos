package com.heuristq.dinein.order.dto;

import com.heuristq.dinein.menu.domain.FoodType;
import com.heuristq.dinein.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ----- Guest requests ----------------------------------------------------------------------

    /** Only identifiers and quantities: prices are always recomputed on the server. */
    public record CartLine(
            @NotNull Long itemId,
            Long variantId,
            @Size(max = 15) List<@NotNull Long> addonIds,
            @Min(1) @Max(50) int quantity,
            @Size(max = 200) String notes) {
    }

    public record PlaceOrderRequest(
            @NotEmpty @Size(max = 50) List<@Valid @NotNull CartLine> items,
            @Size(max = 300) String notes,
            @Size(max = 60) String customerName,
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String customerPhone) {
    }

    /** One offending cart line, so the guest UI can update or remove it. */
    public record CartProblem(int lineIndex, Long itemId, Long variantId, Long addonId, String name, String reason) {
    }

    // ----- Views -------------------------------------------------------------------------------

    public record AddonView(String name, BigDecimal price) {
    }

    public record OrderLineView(Long itemId, Long variantId, String name, String variantName, FoodType foodType,
                                List<AddonView> addons, BigDecimal unitPrice, int quantity, BigDecimal gstPercent,
                                BigDecimal lineTotal, BigDecimal taxAmount, String notes) {
    }

    public record BillView(BigDecimal subtotal, BigDecimal taxTotal, BigDecimal cgst, BigDecimal sgst,
                           BigDecimal grandTotal, boolean pricesIncludeGst) {
    }

    public record PaymentView(String provider, String status, String method, String providerPaymentId,
                              String providerOrderId, Long amountPaise, String failureReason, String refundStatus,
                              String providerRefundId, Instant updatedAt) {
    }

    public record GuestOrderView(Long id, String orderNumber, int displayToken, OrderStatus status, String tableLabel,
                                 String customerName, String notes, List<OrderLineView> items, BillView bill,
                                 PaymentView payment, boolean canRetryPayment, Instant placedAt, Instant paidAt,
                                 Instant preparingAt, Instant readyAt, Instant completedAt, Instant cancelledAt) {
    }

    public record GuestOrderSummary(Long id, String orderNumber, int displayToken, OrderStatus status,
                                    BigDecimal grandTotal, int itemCount, Instant placedAt) {
    }

    public record KitchenLineView(String name, String variantName, FoodType foodType, List<String> addons,
                                  int quantity, String notes) {
    }

    public record KitchenOrderView(Long id, String orderNumber, int displayToken, OrderStatus status,
                                   String tableLabel, String notes, List<KitchenLineView> items, Instant paidAt,
                                   Instant preparingAt, Instant readyAt) {
    }

    public record AdminOrderSummary(Long id, String orderNumber, int displayToken, OrderStatus status,
                                    String tableLabel, String customerName, BigDecimal grandTotal, int itemCount,
                                    String paymentMethod, boolean paymentFlagged, Instant placedAt, Instant paidAt) {
    }

    public record AdminOrderView(Long id, String orderNumber, int displayToken, OrderStatus status, String tableLabel,
                                 String customerName, String customerPhone, String notes, List<OrderLineView> items,
                                 BillView bill, List<PaymentView> payments, boolean paymentFlagged, String flagReason,
                                 String cancelReason, Instant placedAt, Instant paidAt, Instant preparingAt,
                                 Instant readyAt, Instant completedAt, Instant cancelledAt) {
    }

    // ----- Staff requests ----------------------------------------------------------------------

    public record StatusChangeRequest(@NotNull OrderStatus status) {
    }

    public record CancelRequest(@Size(max = 300) String reason) {
    }
}
