package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.CancelRequest;
import com.heuristq.dinein.order.dto.OrderDtos.StatusChangeRequest;
import com.heuristq.dinein.payment.RefundService;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private static final Set<OrderStatus> ADMIN_TARGETS =
            EnumSet.of(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED);

    private final OrderQueryService queryService;
    private final OrderLifecycleService lifecycle;
    private final RefundService refundService;

    public AdminOrderController(OrderQueryService queryService, OrderLifecycleService lifecycle,
                                RefundService refundService) {
        this.queryService = queryService;
        this.lifecycle = lifecycle;
        this.refundService = refundService;
    }

    @GetMapping
    public PageResponse<AdminOrderSummary> list(
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return queryService.adminOrders(status, date, q, page, size);
    }

    @GetMapping("/{id}")
    public AdminOrderView get(@PathVariable Long id) {
        return queryService.adminOrder(id);
    }

    @PatchMapping("/{id}/status")
    public AdminOrderView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        lifecycle.changeByStaff(id, request.status(), ADMIN_TARGETS, "user:" + CurrentStaff.require().userId());
        return queryService.adminOrder(id);
    }

    /** Cancels a paid order and refunds it in full. Calling again retries a failed refund. */
    @PostMapping("/{id}/cancel")
    public AdminOrderView cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request) {
        refundService.cancelAndRefund(id, request == null ? null : request.reason(),
                "user:" + CurrentStaff.require().userId());
        return queryService.adminOrder(id);
    }
}
