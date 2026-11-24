package com.heuristiq.payment.client;

import com.heuristiq.payment.shared.money.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.payment.client.dto.RideDetailsResponse;
import com.heuristiq.payment.events.DurableEventOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideServiceClient {
    private final WebClient rideServiceWebClient;
    private final com.heuristiq.resilient.ResilientCaller rideResilientCaller;
    private final DurableEventOutbox outbox;
    private final ObjectMapper mapper;
    public Optional<RideDetailsResponse> getRide(String rideId) {
        // Dependency errors must not look like a missing ride or silently permit invalid payments.
        return Optional.ofNullable(rideResilientCaller.callBlocking(() -> rideServiceWebClient.get().uri("/rides/{rideId}", rideId)
                .retrieve().bodyToMono(RideDetailsResponse.class), com.heuristiq.resilient.Idempotency.SAFE));
    }
    @Transactional
    public void notifyPaymentResult(String rideId, String status, String paymentId, Money finalAmount) {
        String id = UUID.randomUUID().toString();
        var event = mapper.createObjectNode();
        event.put("eventId", id);
        event.put("rideId", rideId);
        event.put("status", status);
        event.put("paymentId", paymentId);
        if (finalAmount != null) event.set("finalAmount", mapper.valueToTree(finalAmount));
        outbox.enqueue(id, "ride-payment-results", rideId, event.toString());
        // Fast path next to the durable outbox: tell the ride right away once this payment has committed, so the
        // driver and customer see the result even while Kafka is slow or down. The ride ignores the repeat because
        // it is keyed by paymentId, and a failure here is fine - the outbox still delivers.
        Runnable direct = () -> applyResultDirectly(rideId, status, paymentId, finalAmount);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    direct.run();
                }
            });
        } else {
            direct.run();
        }
    }

    private void applyResultDirectly(String rideId, String status, String paymentId, Money finalAmount) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("status", status);
        body.put("paymentId", paymentId);
        if (finalAmount != null) body.put("finalAmount", finalAmount);
        try {
            rideResilientCaller.callBlocking(() -> rideServiceWebClient.post()
                    .uri("/rides/{rideId}/payment-result", rideId)
                    .bodyValue(body)
                    .retrieve().toBodilessEntity(), com.heuristiq.resilient.Idempotency.SAFE);
        } catch (Exception e) {
            log.warn("Direct payment-result call for ride {} failed (the outbox will retry): {}", rideId, e.getMessage());
        }
    }
}
