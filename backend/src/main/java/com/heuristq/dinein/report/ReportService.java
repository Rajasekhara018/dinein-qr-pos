package com.heuristq.dinein.report;

import com.heuristq.dinein.order.OrderQueryService;
import com.heuristq.dinein.order.PricingService;
import com.heuristq.dinein.report.dto.ReportDtos.DailyPoint;
import com.heuristq.dinein.report.dto.ReportDtos.Dashboard;
import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
import com.heuristq.dinein.report.dto.ReportDtos.MethodSplit;
import com.heuristq.dinein.report.dto.ReportDtos.OrderTypeSplit;
import com.heuristq.dinein.report.dto.ReportDtos.PaymentChannelSplit;
import com.heuristq.dinein.report.dto.ReportDtos.SalesSummary;
import com.heuristq.dinein.report.dto.ReportDtos.TopItem;
import com.heuristq.dinein.report.dto.ReportDtos.WaiterPerformance;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.util.BusinessTime;
import com.heuristq.dinein.shared.util.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sales reporting with SQL aggregates over snapshot data (orders/order_item), so past bills never change. Every
 * query here is raw JDBC (not JPA), so there is no automatic tenant scoping from an entity-level predicate -- each
 * one must filter on {@code restaurant_id} itself, via {@link #scope}. A {@code platformAdmin} account (see
 * {@code StaffUserEntity}) is the one deliberate exception: it's the platform operator's own login, so its reports
 * aggregate across every restaurant instead of being confined to one -- {@link #scope} returns no filter for it.
 */
@Service
public class ReportService {

    private static final String PAID = "('CONFIRMED','PREPARING','READY','COMPLETED')";
    private static final int MAX_RANGE_DAYS = 366;
    private static final String ONLINE_CHANNEL = "ONLINE";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final NamedParameterJdbcTemplate jdbc;
    private final OrderQueryService orderQueryService;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, OrderQueryService orderQueryService, Clock clock) {
        this.jdbc = jdbc;
        this.orderQueryService = orderQueryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SalesSummary summary(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = range(from, to);
        Map<String, Object> totals = jdbc.queryForMap("SELECT count(*) AS cnt, coalesce(sum(grand_total),0) AS gross, "
                + "coalesce(sum(tax_total),0) AS tax FROM orders WHERE status IN " + PAID
                + " AND placed_at >= :start AND placed_at < :end" + scope(""), p);
        long count = ((Number) totals.get("cnt")).longValue();
        BigDecimal gross = Money.round((BigDecimal) totals.get("gross"));
        BigDecimal tax = Money.round((BigDecimal) totals.get("tax"));
        BigDecimal[] split = PricingService.splitGst(tax);

        Map<String, Object> cancelled = jdbc.queryForMap("SELECT count(*) AS cnt FROM orders WHERE status = 'CANCELLED' "
                + "AND placed_at >= :start AND placed_at < :end" + scope(""), p);
        BigDecimal refunded = jdbc.queryForObject("SELECT coalesce(sum(coalesce(pm.captured_amount_paise, pm.amount_paise)),0) "
                + "FROM payment pm JOIN orders o ON o.id = pm.order_id WHERE pm.refund_status = 'PROCESSED' "
                + "AND o.placed_at >= :start AND o.placed_at < :end" + scope("o"), p, BigDecimal.class);

        List<MethodSplit> methods = jdbc.query("SELECT coalesce(pm.method,'unknown') AS method, count(*) AS cnt, "
                        + "coalesce(sum(o.grand_total),0) AS amount FROM orders o "
                        + "JOIN payment pm ON pm.order_id = o.id AND pm.status IN ('CAPTURED','REFUNDED') "
                        + "WHERE o.status IN " + PAID + " AND o.placed_at >= :start AND o.placed_at < :end"
                        + scope("o") + " GROUP BY 1 ORDER BY amount DESC", p,
                (rs, i) -> new MethodSplit(rs.getString("method"), rs.getLong("cnt"), Money.round(rs.getBigDecimal("amount"))));

        // ONLINE vs each offline method: how much of the takings is cash in the drawer.
        Map<String, PaymentChannelSplit> channels = new LinkedHashMap<>();
        channels.put(ONLINE_CHANNEL, new PaymentChannelSplit(ONLINE_CHANNEL, 0, ZERO));
        for (OfflinePaymentMethod m : OfflinePaymentMethod.values()) {
            channels.put(m.name(), new PaymentChannelSplit(m.name(), 0, ZERO));
        }
        jdbc.query("SELECT CASE WHEN pm.provider = 'OFFLINE' THEN coalesce(pm.method, 'CASH') ELSE '" + ONLINE_CHANNEL
                + "' END AS channel, count(*) AS cnt, coalesce(sum(o.grand_total),0) AS amount FROM orders o "
                + "JOIN payment pm ON pm.order_id = o.id AND pm.status IN ('CAPTURED','REFUNDED') "
                + "WHERE o.status IN " + PAID + " AND o.placed_at >= :start AND o.placed_at < :end"
                + scope("o") + " GROUP BY 1", p, rs -> {
            String channel = rs.getString("channel");
            channels.put(channel, new PaymentChannelSplit(channel, rs.getLong("cnt"), Money.round(rs.getBigDecimal("amount"))));
        });

        Map<String, OrderTypeSplit> types = new LinkedHashMap<>();
        for (OrderType t : OrderType.values()) {
            types.put(t.name(), new OrderTypeSplit(t.name(), 0, ZERO));
        }
        jdbc.query("SELECT order_type, count(*) AS cnt, coalesce(sum(grand_total),0) AS amount FROM orders "
                + "WHERE status IN " + PAID + " AND placed_at >= :start AND placed_at < :end"
                + scope("") + " GROUP BY order_type", p, rs -> {
            String type = rs.getString("order_type");
            types.put(type, new OrderTypeSplit(type, rs.getLong("cnt"), Money.round(rs.getBigDecimal("amount"))));
        });

        BigDecimal manualRefunds = jdbc.queryForObject("SELECT coalesce(sum(coalesce(pm.captured_amount_paise, pm.amount_paise)),0) "
                + "FROM payment pm JOIN orders o ON o.id = pm.order_id WHERE pm.refund_status = 'MANUAL' "
                + "AND o.placed_at >= :start AND o.placed_at < :end" + scope("o"), p, BigDecimal.class);

        List<TopItem> topItems = jdbc.query("SELECT oi.item_name AS name, sum(oi.quantity) AS qty, "
                        + "coalesce(sum(oi.line_total),0) AS revenue FROM order_item oi JOIN orders o ON o.id = oi.order_id "
                        + "WHERE o.status IN " + PAID + " AND o.placed_at >= :start AND o.placed_at < :end"
                        + scope("o") + " GROUP BY oi.item_name ORDER BY qty DESC, revenue DESC LIMIT 10", p,
                (rs, i) -> new TopItem(rs.getString("name"), rs.getLong("qty"), Money.round(rs.getBigDecimal("revenue"))));

        List<DailyPoint> daily = jdbc.query("SELECT (placed_at AT TIME ZONE 'Asia/Kolkata')::date AS d, count(*) AS cnt, "
                        + "coalesce(sum(grand_total),0) AS gross FROM orders WHERE status IN " + PAID
                        + " AND placed_at >= :start AND placed_at < :end" + scope("")
                        + " GROUP BY 1 ORDER BY 1", p,
                (rs, i) -> new DailyPoint(rs.getDate("d").toLocalDate(), rs.getLong("cnt"), Money.round(rs.getBigDecimal("gross"))));

        // Staff-placed orders only (waiter/counter); guest self-orders have no placed_by_staff_id.
        List<WaiterPerformance> waiterPerformance = jdbc.query(
                "SELECT o.placed_by_staff_id AS staff_id, coalesce(su.display_name, su.username) AS staff_name, "
                        + "count(*) AS cnt, coalesce(sum(o.grand_total),0) AS amount FROM orders o "
                        + "JOIN staff_user su ON su.id = o.placed_by_staff_id "
                        + "WHERE o.status IN " + PAID + " AND o.placed_by_staff_id IS NOT NULL "
                        + "AND o.placed_at >= :start AND o.placed_at < :end" + scope("o")
                        + " GROUP BY 1, 2 ORDER BY amount DESC", p,
                (rs, i) -> new WaiterPerformance(rs.getLong("staff_id"), rs.getString("staff_name"),
                        rs.getLong("cnt"), Money.round(rs.getBigDecimal("amount"))));

        BigDecimal avg = count == 0 ? BigDecimal.ZERO.setScale(2) : gross.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new SalesSummary(from, to, count, gross, tax, split[0], split[1], Money.round(gross.subtract(tax)), avg,
                ((Number) cancelled.get("cnt")).longValue(), Money.fromPaise(refunded == null ? 0 : refunded.longValue()),
                Money.fromPaise(manualRefunds == null ? 0 : manualRefunds.longValue()), methods,
                List.copyOf(channels.values()), List.copyOf(types.values()), topItems, daily, waiterPerformance);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        LocalDate today = BusinessTime.today(clock);
        MapSqlParameterSource p = range(today, today);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, count(*) AS cnt FROM orders WHERE placed_at >= :start AND placed_at < :end"
                + scope("") + " GROUP BY status", p,
                rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("cnt"));
                });
        Map<String, Object> totals = jdbc.queryForMap("SELECT count(*) AS cnt, coalesce(sum(grand_total),0) AS gross FROM orders "
                + "WHERE status IN " + PAID + " AND placed_at >= :start AND placed_at < :end" + scope(""), p);
        long count = ((Number) totals.get("cnt")).longValue();
        BigDecimal gross = Money.round((BigDecimal) totals.get("gross"));
        Long active = jdbc.queryForObject("SELECT count(*) FROM orders WHERE status IN ('CONFIRMED','PREPARING','READY')"
                + scope(""), p, Long.class);
        Long flagged = jdbc.queryForObject("SELECT count(*) FROM orders WHERE payment_flagged = true AND status = 'PENDING_PAYMENT'"
                + scope(""), p, Long.class);
        BigDecimal avg = count == 0 ? BigDecimal.ZERO.setScale(2) : gross.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new Dashboard(today, count, gross, avg, byStatus, active == null ? 0 : active, flagged == null ? 0 : flagged,
                orderQueryService.adminOrders(null, null, null, null, 0, 10).content());
    }

    /** Streams one CSV row per order in the range (all statuses) for accounting. */
    @Transactional(readOnly = true)
    public void writeOrdersCsv(LocalDate from, LocalDate to, Writer out) throws IOException {
        MapSqlParameterSource p = range(from, to);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        out.write("order_number,token,placed_at_ist,status,order_type,table,customer_name,subtotal,tax_total,grand_total,"
                + "payment_provider,payment_method,payment_id,refund_status,placed_by\n");
        jdbc.query("SELECT o.order_number, o.display_token, o.placed_at, o.status, o.order_type, t.label, o.customer_name, "
                + "o.subtotal, o.tax_total, o.grand_total, pm.provider, pm.method, pm.provider_payment_id, pm.refund_status, "
                + "su.username AS placed_by FROM orders o "
                + "LEFT JOIN dining_table t ON t.id = o.table_id "
                + "LEFT JOIN staff_user su ON su.id = o.placed_by_staff_id "
                + "LEFT JOIN LATERAL (SELECT * FROM payment x WHERE x.order_id = o.id ORDER BY (x.status = 'CAPTURED') DESC, x.id DESC LIMIT 1) pm ON true "
                + "WHERE o.placed_at >= :start AND o.placed_at < :end" + scope("o")
                + " ORDER BY o.placed_at", p, rs -> {
            try {
                Timestamp placed = rs.getTimestamp("placed_at");
                out.write(String.join(",",
                        csv(rs.getString("order_number")), String.valueOf(rs.getInt("display_token")),
                        csv(placed.toInstant().atZone(BusinessTime.ZONE).format(fmt)), csv(rs.getString("status")),
                        csv(rs.getString("order_type")), csv(rs.getString("label")), csv(rs.getString("customer_name")),
                        rs.getBigDecimal("subtotal").toPlainString(), rs.getBigDecimal("tax_total").toPlainString(),
                        rs.getBigDecimal("grand_total").toPlainString(), csv(rs.getString("provider")),
                        csv(rs.getString("method")), csv(rs.getString("provider_payment_id")),
                        csv(rs.getString("refund_status")), csv(rs.getString("placed_by"))));
                out.write("\n");
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        out.flush();
    }

    private MapSqlParameterSource range(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "Choose a valid date range");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Date range cannot exceed one year");
        }
        // Always bound, even for a platformAdmin whose queries never reference it (see scope()) -- an unused
        // named parameter is harmless, and this keeps range() simple regardless of who's calling.
        return new MapSqlParameterSource()
                .addValue("start", Timestamp.from(BusinessTime.startOfDay(from)))
                .addValue("end", Timestamp.from(BusinessTime.startOfDay(to.plusDays(1))))
                .addValue("restaurantId", CurrentStaff.require().restaurantId());
    }

    /**
     * {@code " AND <alias.>restaurant_id = :restaurantId"} for an ordinary owner/manager, or {@code ""} for a
     * platform admin (whose reports intentionally aggregate every restaurant). {@code alias} is the query's table
     * alias for {@code orders} (e.g. {@code "o"}), or {@code ""} when the query selects from {@code orders}
     * unaliased.
     */
    private String scope(String alias) {
        if (CurrentStaff.require().platformAdmin()) {
            return "";
        }
        String prefix = alias.isEmpty() ? "" : alias + ".";
        return " AND " + prefix + "restaurant_id = :restaurantId";
    }

    /** RFC 4180 quoting, plus a leading quote for values that spreadsheets would treat as formulas. */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
