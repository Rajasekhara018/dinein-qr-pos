package com.heuristq.dinein.payment;

import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.payment.gateway.PaymentGatewayRegistry;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.shared.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Expires orders left in PENDING_PAYMENT beyond the payment timeout. Before expiring, it asks the provider for the
 * real status so an order whose webhook was delayed is confirmed instead of being expired.
 */
@Slf4j
@Component
public class PaymentExpiryJob {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRegistry gateways;
    private final PaymentStateService paymentStateService;
    private final Duration timeout;
    private final Clock clock;

    public PaymentExpiryJob(OrderRepository orderRepository, PaymentRepository paymentRepository,
                            PaymentGatewayRegistry gateways, PaymentStateService paymentStateService,
                            AppProperties properties, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateways = gateways;
        this.paymentStateService = paymentStateService;
        this.timeout = properties.orders().paymentTimeout();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiry-check-interval-ms}", initialDelay = 30_000)
    public void run() {
        List<Long> ids = orderRepository.findIdsByStatusPlacedBefore(OrderStatus.PENDING_PAYMENT, clock.instant().minus(timeout));
        for (Long orderId : ids) {
            try {
                reconcile(orderId);
            } catch (RuntimeException e) {
                // Provider unreachable etc.: leave the order for the next run rather than risk expiring a paid order.
                log.warn("payment.expiry.skipped orderId={} reason={}", orderId, e.getMessage());
            }
        }
    }

    void reconcile(Long orderId) {
        List<PaymentEntity> attempts = paymentRepository.findByOrderIdOrderByIdAsc(orderId);
        boolean anyFailedAttempt = false;
        for (PaymentEntity attempt : attempts) {
            List<ProviderPayment> remote = gateways.get(attempt.getProvider()).fetchOrderPayments(attempt.getProviderOrderId());
            Optional<ProviderPayment> captured = remote.stream().filter(ProviderPayment::isCaptured).findFirst();
            if (captured.isPresent()) {
                log.info("payment.expiry.late_capture_found orderId={}", orderId);
                paymentStateService.apply(attempt.getProvider(), captured.get(), "expiry-job");
                return;
            }
            anyFailedAttempt |= attempt.getStatus() == PaymentStatus.FAILED
                    || remote.stream().anyMatch(p -> p.outcome() == ProviderPayment.Outcome.FAILED);
        }
        OrderStatus terminal = anyFailedAttempt ? OrderStatus.PAYMENT_FAILED : OrderStatus.EXPIRED;
        if (paymentStateService.expire(orderId, terminal)) {
            log.info("payment.expiry.expired orderId={} status={}", orderId, terminal);
        }
    }
}
