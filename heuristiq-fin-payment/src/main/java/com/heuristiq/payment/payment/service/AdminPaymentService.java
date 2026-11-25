package com.heuristiq.payment.payment.service;

import com.heuristiq.payment.config.MoneyProperties;
import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.payment.payment.PaymentConstants;
import com.heuristiq.payment.payment.dto.RefundRequest;
import com.heuristiq.payment.payment.dto.admin.AdminPayment;
import com.heuristiq.payment.payment.dto.admin.AdminPaymentPage;
import com.heuristiq.payment.payment.dto.admin.AdminPaymentSummary;
import com.heuristiq.payment.payment.dto.admin.AdminPaymentTransition;
import com.heuristiq.payment.payment.lifecycle.PaymentLifecycleService;
import com.heuristiq.payment.payment.dto.admin.AdminRefundRequest;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.model.RefundStatus;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.security.TenantAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Read/refund operations behind the service-authenticated admin API
 * (/api/v1/internal/admin/payments, consumed by Rider_AdminAPI). All filtering, paging and
 * aggregation is done in the database via JPQL. Refunds delegate to {@link PaymentRefundService}
 * — the exact logic behind POST /api/v1/payment/payments/{id}/refund — after admin-specific pre-checks
 * that give distinct 404/409/400 outcomes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    public static final int MAX_PAGE_SIZE = PaymentConstants.ADMIN_MAX_PAGE_SIZE;

    private final EntityManager entityManager;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundService paymentRefundService;
    private final com.heuristiq.payment.payment.repository.PaymentRefundRepository refundRepository;
    private final PaymentStatusPollingService paymentStatusPollingService;
    private final PaymentLifecycleService paymentLifecycleService;
    private final MoneyProperties moneyProperties;

    /** Raw filter values as received on the query string; parsed/validated here (400 on bad input). */
    public record Filter(String customerId, String rideId, String driverId, String status, String method,
                         String from, String to) {
    }

    @Transactional(readOnly = true)
    public AdminPaymentPage list(Filter filter, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        PaymentStatus status = parseEnum(PaymentStatus.class, filter.status(), "status");
        PaymentMethod method = parseEnum(PaymentMethod.class, filter.method(), "method");
        Instant from = parseInstant(filter.from(), "from");
        Instant to = parseInstant(filter.to(), "to");
        requireOrderedWindow(from, to);

        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (hasText(filter.customerId())) {
            where.append(" AND p.customerId = :customerId");
            params.put("customerId", filter.customerId().trim());
        }
        if (hasText(filter.rideId())) {
            where.append(" AND p.rideId = :rideId");
            params.put("rideId", filter.rideId().trim());
        }
        if (hasText(filter.driverId())) {
            where.append(" AND p.driverId = :driverId");
            params.put("driverId", filter.driverId().trim());
        }
        if (status != null) {
            where.append(" AND p.status = :status");
            params.put("status", status);
        }
        if (method != null) {
            where.append(" AND p.method = :method");
            params.put("method", method);
        }
        appendWindow(where, params, from, to);
        appendTenant(where, params);

        TypedQuery<Payment> rows = entityManager.createQuery(
                "SELECT p FROM Payment p" + where + " ORDER BY p.createdAt DESC, p.internalPaymentId DESC",
                Payment.class);
        TypedQuery<Long> count = entityManager.createQuery("SELECT COUNT(p) FROM Payment p" + where, Long.class);
        params.forEach((k, v) -> {
            rows.setParameter(k, v);
            count.setParameter(k, v);
        });

        long total = count.getSingleResult();
        List<AdminPayment> content = rows.setFirstResult(safePage * safeSize).setMaxResults(safeSize)
                .getResultList().stream().map(AdminPayment::from).toList();
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        return new AdminPaymentPage(content, total, pages, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public AdminPayment get(String paymentId) {
        return AdminPayment.from(findOr404(paymentId));
    }

    @Transactional(readOnly = true)
    public AdminPaymentSummary summary(String fromRaw, String toRaw) {
        Instant from = parseInstant(fromRaw, "from");
        Instant to = parseInstant(toRaw, "to");
        requireOrderedWindow(from, to);

        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        appendWindow(where, params, from, to);
        appendTenant(where, params);

        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (PaymentStatus s : PaymentStatus.values()) {
            countByStatus.put(s.name(), 0L);
        }
        long totalCount = 0;
        BigDecimal capturedAmount = BigDecimal.ZERO;
        BigDecimal fullyRefundedAmount = BigDecimal.ZERO;
        TypedQuery<Object[]> byStatus = entityManager.createQuery(
                "SELECT p.status, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p" + where + " GROUP BY p.status",
                Object[].class);
        params.forEach(byStatus::setParameter);
        for (Object[] row : byStatus.getResultList()) {
            PaymentStatus s = (PaymentStatus) row[0];
            long c = ((Number) row[1]).longValue();
            BigDecimal amt = toMoney(row[2]);
            totalCount += c;
            if (s == null) {
                continue;
            }
            countByStatus.put(s.name(), c);
            if (s == PaymentStatus.CAPTURED || s == PaymentStatus.SETTLED) {
                capturedAmount = capturedAmount.add(amt);
            }
            if (s == PaymentStatus.REFUNDED) {
                fullyRefundedAmount = fullyRefundedAmount.add(amt);
            }
        }

        // Partial refunds: the payments row only carries the original amount, so sum the COMPLETED rows of
        // the refund ledger (payment_refunds) plus refund credits written before that ledger existed
        // (wallet REFUND rows without an idempotency key).
        String partialPayments = "SELECT p.internalPaymentId FROM Payment p" + where + " AND p.status = :partialStatus";
        TypedQuery<Number> ledgerPartial = entityManager.createQuery(
                "SELECT COALESCE(SUM(r.amount), 0) FROM PaymentRefund r WHERE r.status = :completed"
                        + " AND r.paymentId IN (" + partialPayments + ")", Number.class);
        params.forEach(ledgerPartial::setParameter);
        ledgerPartial.setParameter("partialStatus", PaymentStatus.PARTIALLY_REFUNDED);
        ledgerPartial.setParameter("completed", RefundStatus.COMPLETED);
        TypedQuery<Number> legacyPartial = entityManager.createQuery(
                "SELECT COALESCE(SUM(w.amount), 0) FROM WalletTransaction w WHERE w.source = 'REFUND'"
                        + " AND w.idempotencyKey IS NULL AND w.referenceId IN (" + partialPayments + ")", Number.class);
        params.forEach(legacyPartial::setParameter);
        legacyPartial.setParameter("partialStatus", PaymentStatus.PARTIALLY_REFUNDED);
        Number ledgerResult = ledgerPartial.getSingleResult();
        Number legacyResult = legacyPartial.getSingleResult();
        BigDecimal partialRefunded = toMoney(ledgerResult).add(toMoney(legacyResult));

        Map<String, AdminPaymentSummary.MethodTotals> byMethod = new LinkedHashMap<>();
        for (PaymentMethod m : PaymentMethod.values()) {
            byMethod.put(m.name(), new AdminPaymentSummary.MethodTotals(0, Money.of(BigDecimal.ZERO, moneyProperties.getCurrency())));
        }
        TypedQuery<Object[]> methodRows = entityManager.createQuery(
                "SELECT p.method, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p" + where + " GROUP BY p.method",
                Object[].class);
        params.forEach(methodRows::setParameter);
        for (Object[] row : methodRows.getResultList()) {
            if (row[0] == null) {
                continue;
            }
            byMethod.put(((PaymentMethod) row[0]).name(), new AdminPaymentSummary.MethodTotals(
                    ((Number) row[1]).longValue(), Money.of(round2(toMoney(row[2])), moneyProperties.getCurrency())));
        }

        String currency = moneyProperties.getCurrency();
        return new AdminPaymentSummary(totalCount, countByStatus, Money.of(round2(capturedAmount), currency),
                Money.of(round2(fullyRefundedAmount.add(partialRefunded)), currency), byMethod);
    }

    /**
     * Admin refund. The pre-checks here exist only to produce precise status codes (404/409/400);
     * the authoritative validation, gateway refund call, state transition and wallet credit all
     * happen in {@link PaymentRefundService#refund} — its own business errors (e.g. gateway
     * rejection) surface as PaymentServiceException -> 400.
     */
    public AdminPayment refund(String paymentId, AdminRefundRequest req) {
        return refund(paymentId, req, null);
    }

    /** As {@link #refund(String, AdminRefundRequest)}, idempotent on the Idempotency-Key header value. */
    public AdminPayment refund(String paymentId, AdminRefundRequest req, String idempotencyKey) {
        if (req == null || !hasText(req.adminUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "adminUserId is required");
        }
        if (req.amount() != null && req.amount().value().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }
        Payment payment = findOr404(paymentId);
        boolean keyReplay = idempotencyKey != null && !idempotencyKey.isBlank()
                && refundRepository.findByIdempotencyKey(idempotencyKey.trim()).isPresent();
        // A replay must reach the refund engine even when the first attempt already made the payment
        // REFUNDED; the engine checks key/payment/amount consistency and returns the original result.
        boolean refundable = keyReplay || payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.SETTLED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED;
        if (!refundable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only CAPTURED, SETTLED or PARTIALLY_REFUNDED payments can be refunded (current status: "
                            + payment.getStatus() + ")");
        }
        if (req.amount() != null && payment.getAmount() != null && req.amount().value().compareTo(payment.getAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount exceeds the captured amount");
        }

        log.info("Admin refund requested: payment={} amount={} adminUserId={} destination={} reference={}",
                paymentId, req.amount() != null ? req.amount() : "FULL", req.adminUserId(),
                req.destination(), req.reference());
        paymentRefundService.refund(paymentId,
                new RefundRequest(req.amount(), req.reason(), req.destination(), req.reference()), req.adminUserId(), idempotencyKey);
        return AdminPayment.from(findOr404(paymentId));
    }

    /**
     * Asks the payment gateway for the payment's live outcome and applies it (INITIATED -> CAPTURED /
     * FAILED / EXPIRED). A payment that is already past INITIATED/AUTHORIZED is returned unchanged.
     */
    @Transactional
    public AdminPayment reconcile(String paymentId) {
        findOr404(paymentId);
        return AdminPayment.from(paymentStatusPollingService.reconcile(paymentId));
    }

    /** The payment's lifecycle, oldest step first. */
    @Transactional(readOnly = true)
    public List<AdminPaymentTransition> transitions(String paymentId) {
        return paymentLifecycleService.history(findOr404(paymentId));
    }

    private Payment findOr404(String paymentId) {
        // Another organization's payment looks like it does not exist.
        return paymentRepository.findById(paymentId)
                .filter(payment -> TenantAccess.canSee(payment.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
    }

    /** Limits a query on {@code Payment p} to the caller's organization. */
    private static void appendTenant(StringBuilder where, Map<String, Object> params) {
        where.append(TenantAccess.jpqlClause("p"));
        TenantAccess.Scope scope = TenantAccess.scope();
        if (!scope.unrestricted() && scope.hasTenant()) {
            params.put("tenantId", scope.tenantId());
        }
    }

    private static void appendWindow(StringBuilder where, Map<String, Object> params, Instant from, Instant to) {
        if (from != null) {
            where.append(" AND p.createdAt >= :from");
            params.put("from", AdminPayment.toLocal(from));
        }
        if (to != null) {
            where.append(" AND p.createdAt <= :to");
            params.put("to", AdminPayment.toLocal(to));
        }
    }

    private static void requireOrderedWindow(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String name) {
        if (!hasText(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown " + name + ": " + raw);
        }
    }

    private static Instant parseInstant(String raw, String name) {
        if (!hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    name + " must be an ISO-8601 instant, e.g. 2026-01-31T00:00:00Z");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** A SUM() result from a JPQL aggregate as an exact decimal; null (no rows) is zero. */
    private static BigDecimal toMoney(Object sum) {
        if (sum == null) {
            return BigDecimal.ZERO;
        }
        return sum instanceof BigDecimal b ? b : new BigDecimal(sum.toString());
    }
}
