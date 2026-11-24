package com.railcab.dms.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, logs the JVM default zone next to the configured legacy zone and warns when their current offsets differ.
 * Informational only: it never fails the start and changes no behaviour.
 */
@Component
public class LegacyZoneStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(LegacyZoneStartupCheck.class);

    private final LegacyZoneProperties properties;
    private final Clock clock;
    private final MeterRegistry registry;
    private final AtomicInteger mismatch = new AtomicInteger();

    @Autowired
    public LegacyZoneStartupCheck(LegacyZoneProperties properties, ObjectProvider<Clock> clock,
                                  ObjectProvider<MeterRegistry> registry) {
        this(properties, clock.getIfUnique(Clock::systemUTC), registry.getIfAvailable());
    }

    LegacyZoneStartupCheck(LegacyZoneProperties properties, Clock clock, MeterRegistry registry) {
        this.properties = properties;
        this.clock = clock;
        this.registry = registry;
    }

    /** Result of one comparison: the INFO line, and the WARN text when the offsets differ (else null). */
    record Outcome(String summary, String warning) {
        boolean mismatch() {
            return warning != null;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            Outcome outcome = evaluate(ZoneId.systemDefault());
            log.info(outcome.summary());
            if (outcome.mismatch()) {
                log.warn(outcome.warning());
            }
        } catch (RuntimeException e) {
            log.warn("Legacy zone check skipped: {}", e.toString());
        }
    }

    Outcome evaluate(ZoneId jvmZone) {
        ZoneId legacy = properties.zone();
        Instant now = clock.instant();
        ZoneOffset jvmOffset = jvmZone.getRules().getOffset(now);
        ZoneOffset legacyOffset = legacy.getRules().getOffset(now);
        boolean differs = !jvmOffset.equals(legacyOffset);
        mismatch.set(differs ? 1 : 0);
        if (registry != null) {
            // strongReference: the registry otherwise holds the counter weakly and the gauge reads NaN once this check is collected
            Gauge.builder(LegacyZoneConstants.MISMATCH_METRIC, mismatch, AtomicInteger::get)
                    .strongReference(true)
                    .register(registry);
        }
        String summary = "Time zones: JVM default zone " + jvmZone + " (offset " + jvmOffset + "), "
                + LegacyZoneConstants.PROPERTY_KEY + " " + legacy + " (offset " + legacyOffset + ")";
        String warning = differs
                ? "JVM zone " + jvmZone + " differs from " + LegacyZoneConstants.PROPERTY_KEY + " " + legacy
                + ": stored zone-less timestamps written by this service are read as " + legacy
                : null;
        return new Outcome(summary, warning);
    }
}
