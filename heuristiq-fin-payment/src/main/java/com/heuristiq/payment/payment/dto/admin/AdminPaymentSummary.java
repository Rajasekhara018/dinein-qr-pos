package com.heuristiq.payment.payment.dto.admin;

import com.heuristiq.payment.shared.money.Money;
import java.math.BigDecimal;
import java.util.Map;

/**
 * GET /api/v1/internal/admin/payments/summary.
 *
 * @param totalCount     all payments created in the window
 * @param countByStatus  every PaymentStatus name -> count (0 when none)
 * @param capturedAmount sum of amount over CAPTURED + SETTLED payments
 * @param refundedAmount REFUNDED payments contribute their full amount (a full refund is by
 *                       definition the whole payment amount); PARTIALLY_REFUNDED payments contribute
 *                       the actual refunded amount recorded in wallet_transactions
 *                       (source=REFUND, reference_id=payment id)
 * @param byMethod       CASH / RAZORPAY -> count and summed amount over all payments (any status) in the window
 */
public record AdminPaymentSummary(long totalCount, Map<String, Long> countByStatus, Money capturedAmount,
                                  Money refundedAmount, Map<String, MethodTotals> byMethod) {

    public record MethodTotals(long count, Money amount) {
    }
}
