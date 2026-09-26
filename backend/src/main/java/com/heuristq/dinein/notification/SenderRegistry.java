package com.heuristq.dinein.notification;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the configured provider for one channel, like {@code PaymentGatewayRegistry} does for payments. An unknown
 * provider code fails at startup so a typo never silently drops messages; a blank one means {@link #LOG}.
 */
@Slf4j
public abstract class SenderRegistry<S> {

    /** Provider that only writes a log line (masked recipient). The default for every channel. */
    public static final String LOG = "LOG";

    private final Map<String, S> senders;
    private final String activeCode;

    protected SenderRegistry(String channel, List<S> senders, Function<S, String> providerOf, String configured) {
        this.senders = senders.stream().collect(Collectors.toMap(s -> providerOf.apply(s).toUpperCase(Locale.ROOT),
                Function.identity()));
        String code = configured == null || configured.isBlank() ? LOG : configured.trim().toUpperCase(Locale.ROOT);
        if (!this.senders.containsKey(code)) {
            throw new IllegalStateException("Unknown " + channel + " provider '" + code + "'. Available: "
                    + this.senders.keySet());
        }
        this.activeCode = code;
        log.info("notifications.active_provider channel={} provider={} available={}", channel, code,
                this.senders.keySet());
    }

    public S active() {
        return senders.get(activeCode);
    }

    public String activeProvider() {
        return activeCode;
    }
}
