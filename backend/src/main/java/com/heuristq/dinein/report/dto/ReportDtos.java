package com.heuristq.dinein.report.dto;

import com.heuristq.dinein.order.dto.OrderDtos.AdminOrderSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record MethodSplit(String method, long count, BigDecimal amount) {
    }

    /** Paid orders per order type (DINE_IN, TAKEAWAY); every type is listed, with zeros when unused. */
    public record OrderTypeSplit(String orderType, long count, BigDecimal amount) {
    }

    /**
     * Paid orders per payment channel: ONLINE (any gateway) or the offline method (CASH, UPI_AT_COUNTER,
     * CARD_AT_COUNTER). Every channel is listed, with zeros when unused; CASH is the cash the counter should hold.
     */
    public record PaymentChannelSplit(String channel, long count, BigDecimal amount) {
    }

    public record TopItem(String name, long quantity, BigDecimal revenue) {
    }

    /** One staff member who placed orders on a guest's behalf (waiter/counter), ranked by revenue. */
    public record WaiterPerformance(Long staffId, String staffName, long ordersCount, BigDecimal revenue) {
    }

    public record DailyPoint(LocalDate date, long orders, BigDecimal gross) {
    }

    /**
     * Sales for paid orders (CONFIRMED, PREPARING, READY, COMPLETED) placed in [from, to] (IST dates, inclusive).
     * {@code net} is revenue excluding GST. {@code refundedAmount} is gateway refunds completed;
     * {@code manualRefundAmount} is offline money of cancelled orders that staff had to hand back.
     */
    public record SalesSummary(LocalDate from, LocalDate to, long ordersCount, BigDecimal gross, BigDecimal tax,
                               BigDecimal cgst, BigDecimal sgst, BigDecimal net, BigDecimal averageOrderValue,
                               long cancelledCount, BigDecimal refundedAmount, BigDecimal manualRefundAmount,
                               List<MethodSplit> paymentMethods, List<PaymentChannelSplit> paymentChannels,
                               List<OrderTypeSplit> orderTypes, List<TopItem> topItems, List<DailyPoint> daily,
                               List<WaiterPerformance> waiterPerformance) {
    }

    public record Dashboard(LocalDate date, long ordersToday, BigDecimal revenueToday, BigDecimal averageOrderValue,
                            Map<String, Long> ordersByStatus, long activeKitchenOrders, long flaggedOrders,
                            List<AdminOrderSummary> recentOrders) {
    }
}
