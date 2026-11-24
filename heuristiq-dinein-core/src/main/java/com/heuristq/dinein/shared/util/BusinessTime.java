package com.heuristq.dinein.shared.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** The restaurant operates in IST; business dates (daily order numbers, reports) are computed in this zone. */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private BusinessTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static LocalTime nowTime(Clock clock) {
        return LocalTime.now(clock.withZone(ZONE));
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    /** True when {@code now} lies in [open, close); supports windows that cross midnight. Null bounds mean always open. */
    public static boolean isWithin(LocalTime open, LocalTime close, LocalTime now) {
        if (open == null || close == null || open.equals(close)) {
            return true;
        }
        if (open.isBefore(close)) {
            return !now.isBefore(open) && now.isBefore(close);
        }
        return !now.isBefore(open) || now.isBefore(close);
    }
}
