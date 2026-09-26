package com.heuristq.dinein.payment.gateway;

import com.heuristq.dinein.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Looks up gateways by code. New payments use the active one; existing payments use the one that created them. */
@Slf4j
@Component
public class PaymentGatewayRegistry {

    /** Same value as {@code PaymentEntity.OFFLINE_PROVIDER}; kept here so the gateway layer has no domain import. */
    static final String OFFLINE = "OFFLINE";

    private final Map<String, PaymentGateway> gateways;
    private final String activeCode;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways, PaymentProperties properties) {
        if (gateways.stream().anyMatch(g -> isOffline(g.code()))) {
            throw new IllegalStateException("'" + OFFLINE + "' is reserved for counter payments and cannot be a gateway");
        }
        this.gateways = gateways.stream().collect(Collectors.toMap(g -> g.code().toUpperCase(Locale.ROOT),
                Function.identity()));
        this.activeCode = properties.provider().trim().toUpperCase(Locale.ROOT);
        if (!this.gateways.containsKey(activeCode)) {
            throw new IllegalStateException("Unknown payment provider '" + activeCode + "'. Available: " + this.gateways.keySet());
        }
        log.info("payments.active_provider={} configured={} available={}", activeCode,
                this.gateways.get(activeCode).isConfigured(), this.gateways.keySet());
    }

    public PaymentGateway active() {
        PaymentGateway gateway = gateways.get(activeCode);
        if (!gateway.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED",
                    "Online payment is not configured");
        }
        return gateway;
    }

    /**
     * @throws ApiException 400 UNKNOWN_PROVIDER for unknown codes and for {@code OFFLINE}: counter payments have no
     *                      gateway, so callers (refunds, expiry, webhooks, verify) must branch on
     *                      {@code PaymentEntity#isOffline()} before looking one up.
     */
    public PaymentGateway get(String code) {
        if (isOffline(code)) {
            throw ApiException.badRequest("UNKNOWN_PROVIDER", "Offline payments have no payment gateway");
        }
        PaymentGateway gateway = code == null ? null : gateways.get(code.toUpperCase(Locale.ROOT));
        if (gateway == null) {
            throw ApiException.badRequest("UNKNOWN_PROVIDER", "Unknown payment provider");
        }
        return gateway;
    }

    private static boolean isOffline(String code) {
        return code != null && OFFLINE.equalsIgnoreCase(code.trim());
    }
}
