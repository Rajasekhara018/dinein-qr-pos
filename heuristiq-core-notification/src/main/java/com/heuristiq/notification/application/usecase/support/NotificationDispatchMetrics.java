package com.railcab.nms.application.usecase.support;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.railcab.nms.application.dto.ChannelSendOutcome;
import com.railcab.nms.application.port.out.ChannelProvider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import static com.railcab.nms.application.usecase.support.DispatchMetricsConstants.*;

/**
 * Owns Micrometer metric recording for dispatch attempts -- split out of
 * {@link NotificationDispatchService} so metric names/tags can change independently of
 * orchestration logic.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatchMetrics {

    private final MeterRegistry meterRegistry;

    public void record(ChannelProvider provider, ChannelSendOutcome outcome, Duration duration) {
        Timer.builder(PROVIDER_SEND_DURATION)
                .tag(TAG_CHANNEL, provider.getChannel().name())
                .tag(TAG_PROVIDER, provider.getClass().getSimpleName())
                .tag(TAG_SUCCESS, Boolean.toString(outcome.success()))
                .register(meterRegistry)
                .record(duration);
        meterRegistry.counter(PROVIDER_SEND_TOTAL,
                        TAG_CHANNEL, provider.getChannel().name(),
                        TAG_PROVIDER, provider.getClass().getSimpleName(),
                        TAG_SUCCESS, Boolean.toString(outcome.success()),
                        TAG_REASON, classifyFailureReason(outcome))
                .increment();
    }

    /** Dispatch never reached a provider.send() attempt at all -- no provider registered for the
     *  channel, no active template for the event/channel, or the recipient resolver came back
     *  empty. Distinct from a provider send failure (recorded above), which at least attempted
     *  delivery. */
    public void recordSkipped(String reason) {
        meterRegistry.counter(DISPATCH_SKIPPED_TOTAL, TAG_REASON, reason).increment();
    }

    // Bucketed, not the raw exception message -- an unbounded reason tag (arbitrary exception
    // text) would blow up cardinality on every distinct downstream error string.
    private String classifyFailureReason(ChannelSendOutcome outcome) {
        if (outcome.success()) return REASON_NONE;
        String error = outcome.error();
        if (error == null) return REASON_UNKNOWN;
        String e = error.toLowerCase();
        if (e.contains("circuit") && e.contains("open")) return REASON_CIRCUIT_OPEN;
        if (e.contains("capacity")) return REASON_BULKHEAD_FULL;
        if (e.contains("returned false")) return REASON_PROVIDER_REJECTED;
        return REASON_EXCEPTION;
    }
}
