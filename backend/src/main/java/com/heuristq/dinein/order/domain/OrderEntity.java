package com.heuristq.dinein.order.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "orders")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class OrderEntity extends TenantOwnedEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(name = "display_token", nullable = false)
    private int displayToken;

    @Column(name = "table_id")
    private Long tableId;

    /** Null for staff-assisted orders (see {@link #placedByStaffId}). */
    @Column(name = "guest_session_id", length = 64)
    private String guestSessionId;

    /** Staff user who placed the order for the guest (waiter / counter); null for QR self-orders. */
    @Column(name = "placed_by_staff_id")
    private Long placedByStaffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType = OrderType.DINE_IN;

    @Column(name = "customer_name", length = 60)
    private String customerName;

    @Column(name = "customer_phone", length = 15)
    private String customerPhone;

    @Column(length = 300)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "prices_include_gst", nullable = false)
    private boolean pricesIncludeGst;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "payment_flagged", nullable = false)
    private boolean paymentFlagged;

    /** Staff-set flag telling the kitchen to work this order first. */
    @Column(nullable = false)
    private boolean priority;

    @Column(name = "flag_reason", length = 300)
    private String flagReason;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "preparing_at")
    private Instant preparingAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    public boolean isPlacedByStaff() {
        return placedByStaffId != null;
    }

    /** True when the order was placed from this guest session (null-safe for staff-assisted orders). */
    public boolean belongsToGuest(String sessionId) {
        return guestSessionId != null && guestSessionId.equals(sessionId);
    }

    public void addItem(OrderItemEntity item) {
        item.setOrder(this);
        items.add(item);
    }
}
