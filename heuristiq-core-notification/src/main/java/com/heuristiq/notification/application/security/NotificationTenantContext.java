package com.railcab.nms.application.security;

import java.util.function.Supplier;

/**
 * Carries the organization of the notification being sent to the channel provider running on the same thread, so
 * a provider that stores records of its own (the in-app channel) can stamp them without the provider contract
 * having to change. The dispatch service sets it around each provider call and always clears it afterwards.
 */
public final class NotificationTenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private NotificationTenantContext() {
    }

    /** Runs {@code action} with {@code tenantId} as the current organization, restoring the previous value after. */
    public static <T> T callAs(String tenantId, Supplier<T> action) {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The organization of the notification being sent on this thread; null outside a dispatch or when it has none. */
    public static String current() {
        return CURRENT.get();
    }
}
