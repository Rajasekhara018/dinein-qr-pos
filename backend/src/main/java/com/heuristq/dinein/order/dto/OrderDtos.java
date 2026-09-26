package com.heuristq.dinein.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.heuristq.dinein.menu.domain.FoodType;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
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

    /** {@code orderType} is optional and defaults to DINE_IN. */
    public record PlaceOrderRequest(
            @NotEmpty @Size(max = 50) List<@Valid @NotNull CartLine> items,
            @Size(max = 300) String notes,
            @Size(max = 60) String customerName,
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String customerPhone,
            OrderType orderType) {
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

    /**
     * {@code provider} is the gateway code or OFFLINE (counter payment; {@code method} is then CASH, UPI_AT_COUNTER or
     * CARD_AT_COUNTER). {@code refundStatus}: PENDING, PROCESSED, FAILED or MANUAL (offline payment of a cancelled
     * order: hand the money back, no gateway involved).
     */
    public record PaymentView(String provider, String status, String method, String providerPaymentId,
                              String providerOrderId, Long amountPaise, String failureReason, String refundStatus,
                              String providerRefundId, Long recordedByStaffId, Instant updatedAt) {
    }

    public record GuestOrderView(Long id, String orderNumber, int displayToken, OrderStatus status,
                                 OrderType orderType, String tableLabel, String customerName, String notes,
                                 List<OrderLineView> items, BillView bill, PaymentView payment,
                                 boolean canRetryPayment, Instant placedAt, Instant paidAt, Instant preparingAt,
                                 Instant readyAt, Instant completedAt, Instant cancelledAt) {
    }

    public record GuestOrderSummary(Long id, String orderNumber, int displayToken, OrderStatus status,
                                    OrderType orderType, BigDecimal grandTotal, int itemCount, Instant placedAt) {
    }

    public record KitchenLineView(String name, String variantName, FoodType foodType, List<String> addons,
                                  int quantity, String notes) {
    }

    /**
     * Kitchen ticket, also used for the waiter's order cards and realtime payloads. {@code placedByStaff} is true for
     * orders taken by a waiter or at the counter.
     */
    public record KitchenOrderView(Long id, String orderNumber, int displayToken, OrderStatus status,
                                   OrderType orderType, Long tableId, String tableLabel, String notes,
                                   List<KitchenLineView> items, boolean placedByStaff, Instant paidAt,
                                   Instant preparingAt, Instant readyAt) {
    }

    /** {@code paymentProvider}: gateway code or OFFLINE; for OFFLINE {@code paymentMethod} is CASH etc. */
    public record AdminOrderSummary(Long id, String orderNumber, int displayToken, OrderStatus status,
                                    OrderType orderType, String tableLabel, String customerName,
                                    BigDecimal grandTotal, int itemCount, String paymentProvider,
                                    String paymentMethod, boolean paymentFlagged, Long placedByStaffId,
                                    Instant placedAt, Instant paidAt) {
    }

    /**
     * {@code manualRefundDue}: the order was cancelled after an OFFLINE payment, so the amount has to be handed back
     * in cash (that payment's {@code refundStatus} is MANUAL and no gateway was called).
     */
    public record AdminOrderView(Long id, String orderNumber, int displayToken, OrderStatus status,
                                 OrderType orderType, String tableLabel, String customerName, String customerPhone,
                                 String notes, List<OrderLineView> items, BillView bill, List<PaymentView> payments,
                                 boolean paymentFlagged, String flagReason, String cancelReason,
                                 Long placedByStaffId, String placedByStaffName, boolean manualRefundDue,
                                 Instant placedAt, Instant paidAt, Instant preparingAt, Instant readyAt,
                                 Instant completedAt, Instant cancelledAt) {
    }

    /** One active table on the waiter screen with its open (paid, not yet served) orders. */
    public record WaiterTableView(Long id, String label, int openOrders, int confirmed, int preparing, int ready,
                                  Instant oldestOpenSince) {
    }

    // ----- Staff requests ----------------------------------------------------------------------

    public record StatusChangeRequest(@NotNull OrderStatus status) {
    }

    public record CancelRequest(@Size(max = 300) String reason) {
    }

    /** How a staff-assisted order is paid: online (same checkout as guests) or taken offline at the table / counter. */
    public enum StaffPaymentMethod {
        CASH,
        UPI_AT_COUNTER,
        CARD_AT_COUNTER,
        ONLINE;

        /** The offline method, or null for ONLINE. */
        public OfflinePaymentMethod offline() {
            return this == ONLINE ? null : OfflinePaymentMethod.valueOf(name());
        }
    }

    /**
     * Order placed by a waiter or at the counter. {@code tableId} is required for DINE_IN and optional for TAKEAWAY;
     * {@code items} have the guest cart shape; {@code orderType} defaults to DINE_IN. {@code note} is also accepted
     * as {@code notes}.
     */
    public record StaffPlaceOrderRequest(
            Long tableId,
            OrderType orderType,
            @NotEmpty @Size(max = 50) List<@Valid @NotNull CartLine> items,
            @JsonAlias("notes") @Size(max = 300) String note,
            @Size(max = 60) String customerName,
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String customerPhone,
            @NotNull StaffPaymentMethod paymentMethod,
            @NotNull @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "must be 8-64 URL-safe characters") String idempotencyKey) {
    }

    public record MarkPaidOfflineRequest(@NotNull OfflinePaymentMethod method) {
    }
}
