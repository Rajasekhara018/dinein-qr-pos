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

    public record TopItem(String name, long quantity, BigDecimal revenue) {
    }

    public record DailyPoint(LocalDate date, long orders, BigDecimal gross) {
    }

    /**
     * Sales for paid orders (CONFIRMED, PREPARING, READY, COMPLETED) placed in [from, to] (IST dates, inclusive).
     * {@code net} is revenue excluding GST.
     */
    public record SalesSummary(LocalDate from, LocalDate to, long ordersCount, BigDecimal gross, BigDecimal tax,
                               BigDecimal cgst, BigDecimal sgst, BigDecimal net, BigDecimal averageOrderValue,
                               long cancelledCount, BigDecimal refundedAmount, List<MethodSplit> paymentMethods,
                               List<TopItem> topItems, List<DailyPoint> daily) {
    }

    public record Dashboard(LocalDate date, long ordersToday, BigDecimal revenueToday, BigDecimal averageOrderValue,
                            Map<String, Long> ordersByStatus, long activeKitchenOrders, long flaggedOrders,
                            List<AdminOrderSummary> recentOrders) {
    }
}
