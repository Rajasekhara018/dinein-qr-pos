package com.heuristiq.audit.application;

import static com.heuristiq.audit.application.IngestConstants.*;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key budget over a fixed one-minute window, kept in memory on this instance (a user spread over several
 * instances can send up to the limit on each). Used to stop a misbehaving client from flooding the store with
 * screen-view events. Stale windows are swept when the map grows.
 */
@Component
public class SlidingWindowRateLimiter {

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Creates the limiter.
     *
     * @param clock time source
     */
    public SlidingWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    private static final class Window {
        long start;
        int used;
    }

    /**
     * Takes up to {@code wanted} units from the key's budget.
     *
     * @param key    who is limited (user id)
     * @param wanted units requested
     * @param limit  units allowed per minute
     * @return units granted, between 0 and {@code wanted}
     */
    public int acquire(String key, int wanted, int limit) {
        long now = clock.millis();
        if (windows.size() > SWEEP_AT) {
            windows.entrySet().removeIf(e -> now - e.getValue().start > 2 * WINDOW_MS);
        }
        int[] granted = new int[1];
        windows.compute(key, (k, w) -> {
            Window win = w;
            if (win == null || now - win.start >= WINDOW_MS) {
                win = new Window();
                win.start = now;
            }
            int room = Math.max(0, limit - win.used);
            granted[0] = Math.min(wanted, room);
            win.used += granted[0];
            return win;
        });
        return granted[0];
    }
}
