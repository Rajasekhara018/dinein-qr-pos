package com.heuristiq.gateway.audit;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A configured list of path patterns kept lowercased, so matching a request does not lowercase every pattern again.
 * The lowercased copy is rebuilt only when the configured list changes (it is compared, not copied, on each use).
 */
final class LowercasedPatterns {

    private final Supplier<List<String>> source;
    private volatile Snapshot snapshot = new Snapshot(List.of(), List.of());

    /**
     * @param source returns the currently configured patterns
     */
    LowercasedPatterns(Supplier<List<String>> source) {
        this.source = source;
    }

    /** @return the configured patterns, lowercased, in the configured order */
    List<String> get() {
        List<String> current = source.get();
        Snapshot s = snapshot;
        if (s.source().equals(current)) {
            return s.lowered();
        }
        List<String> copy = List.copyOf(current);
        List<String> lowered = copy.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList();
        snapshot = new Snapshot(copy, lowered);
        return lowered;
    }

    private record Snapshot(List<String> source, List<String> lowered) {
    }
}
