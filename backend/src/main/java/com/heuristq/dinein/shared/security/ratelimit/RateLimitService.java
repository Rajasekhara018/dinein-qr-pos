package com.heuristq.dinein.shared.security.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding-window rate limiter (single backend instance, as per MVP scope). */
@Service
public class RateLimitService {

    private static final long MAX_WINDOW_MILLIS = 60 * 60 * 1000L;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Decision allow(String key, RateLimitProperties.Rule rule) {
        if (!properties.enabled() || rule == null || rule.limit() < 1 || rule.windowSeconds() < 1) {
            return Decision.ALLOWED;
        }
        long now = clock.millis();
        long windowMillis = rule.windowSeconds() * 1000L;
        Deque<Long> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() >= windowMillis) {
                bucket.removeFirst();
            }
            if (bucket.size() >= rule.limit()) {
                long retryAfterMillis = windowMillis - (now - bucket.peekFirst());
                return new Decision(false, Math.max(1L, (retryAfterMillis + 999L) / 1000L));
            }
            bucket.addLast(now);
            return Decision.ALLOWED;
        }
    }

    /** Drops buckets whose newest hit is older than any configured window so memory stays bounded. */
    @Scheduled(fixedDelay = 600_000)
    public void evictStale() {
        long now = clock.millis();
        buckets.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last > MAX_WINDOW_MILLIS;
            }
        });
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }
}
