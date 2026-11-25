package com.heuristiq.payment.wallet.dto;

import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.payment.payment.dto.RazorpayOrderDetails;
import com.heuristiq.payment.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.payment.wallet.model.WalletTopupSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.math.BigDecimal;

/**
 * @param razorpayOrder  Populated ONLY for gateway=RAZORPAY, right after creation — same shape
 *                        Ride/Dispatch's existing PaymentResponse.razorpayOrder callers already use.
 * @param gatewaySession Populated for gateway=PAYU/TOUCAN/PINELABS right after creation.
 */
public record WalletTopupResponse(
        String topupId,
        String customerId,
        Money amount,
        String status,
        String gateway,
        String providerOrderId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RazorpayOrderDetails razorpayOrder,
        Map<String, Object> gatewaySession
) {
    public static WalletTopupResponse from(WalletTopupSession s) {
        return from(s, null);
    }

    public static WalletTopupResponse from(WalletTopupSession s, GatewaySessionResult session) {
        return new WalletTopupResponse(
                s.getId(),
                s.getCustomerId(),
                Money.ofNullable(s.getAmount(), s.getCurrency()),
                s.getStatus().name(),
                s.getGateway() != null ? s.getGateway().name() : null,
                s.getProviderOrderId(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                session != null ? session.razorpayOrder() : null,
                session != null ? session.gatewaySession() : null
        );
    }
}
