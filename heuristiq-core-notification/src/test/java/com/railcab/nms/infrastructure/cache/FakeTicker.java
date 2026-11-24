package com.railcab.nms.infrastructure.cache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;

/** A manually advanced clock, so expiry tests never sleep. */
final class FakeTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
        return nanos.get();
    }

    void advance(Duration by) {
        nanos.addAndGet(by.toNanos());
    }
}
