package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.shared.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * GDPR data minimisation: once a finished order is older than the configured retention period, its guest-identifying
 * fields (name, phone, notes) are cleared. Amounts, item lines and status are kept -- reporting still works, but the
 * guest can no longer be identified from that order. See {@link OrderRepository#redactGuestPiiOlderThan}.
 */
@Slf4j
@Component
public class GuestDataRetentionJob {

    private final OrderRepository orderRepository;
    private final Duration retention;
    private final Clock clock;

    public GuestDataRetentionJob(OrderRepository orderRepository, AppProperties properties, Clock clock) {
        this.orderRepository = orderRepository;
        this.retention = Duration.ofDays(properties.privacy().guestDataRetentionDays());
        this.clock = clock;
    }

    @Scheduled(cron = "${app.privacy.guest-data-redaction-cron}", zone = "Asia/Kolkata")
    @Transactional
    public void run() {
        int redacted = orderRepository.redactGuestPiiOlderThan(OrderStatus.TERMINAL, clock.instant().minus(retention));
        if (redacted > 0) {
            log.info("privacy.guest_data_redacted count={}", redacted);
        }
    }
}
