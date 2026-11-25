package com.heuristiq.payment.payment.dto.admin;
import com.heuristiq.payment.shared.money.Money;
import java.math.BigDecimal;

/**
 * POST /api/v1/internal/admin/payments/{paymentId}/refund.
 *
 * @param amount      optional; null means a refund of the whole remaining refundable amount
 * @param reason      passed through to the gateway refund call
 * @param adminUserId required — the operator performing the refund (audit log and refund ledger)
 * @param destination optional, GATEWAY or WALLET; default GATEWAY for gateway payments, WALLET for cash
 * @param reference   optional unique refund reference making the request idempotent
 */
public record AdminRefundRequest(Money amount, String reason, String adminUserId, String destination,
                                 String reference) {

    /** Contract of the original endpoint. */
    public AdminRefundRequest(Money amount, String reason, String adminUserId) {
        this(amount, reason, adminUserId, null, null);
    }
}
