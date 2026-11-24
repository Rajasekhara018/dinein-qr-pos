package com.heuristiq.notification.application.usecase.support;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding-window limit on how many test messages one caller may send. In memory and per instance, which
 * is enough for a guard against misuse; memory is bounded by {@link TemplateTestSendConstants#MAX_TRACKED_CALLERS}.
 */
public class TestSendRateLimiter {

    private final Clock clock;
    private final int maxPerWindow;
    private final Map<String, Deque<Instant>> sends = new HashMap<>();

    public TestSendRateLimiter(Clock clock, int maxPerWindow) {
        this.clock = clock;
        this.maxPerWindow = maxPerWindow;
    }

    /** Records one send for the caller and returns true, or returns false (recording nothing) when over the limit. */
    public synchronized boolean tryAcquire(String callerId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(TemplateTestSendConstants.RATE_WINDOW);
        if (sends.size() >= TemplateTestSendConstants.MAX_TRACKED_CALLERS && !sends.containsKey(callerId)) {
            sends.values().removeIf(times -> times.isEmpty() || times.peekLast().isBefore(windowStart));
            if (sends.size() >= TemplateTestSendConstants.MAX_TRACKED_CALLERS) {
                return false;
            }
        }
        Deque<Instant> times = sends.computeIfAbsent(callerId, id -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst().isBefore(windowStart)) {
            times.removeFirst();
        }
        if (times.size() >= maxPerWindow) {
            return false;
        }
        times.addLast(now);
        return true;
    }
}
