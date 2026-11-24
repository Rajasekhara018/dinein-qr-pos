package com.heuristiq.paymentservice.payment.lifecycle;

/**
 * Names what is driving the status change currently being made on this thread (a webhook, the
 * status poll, an admin refund, ...), so the history row can say so. Optional: when nothing is set
 * the listener infers a source from the new status.
 */
public final class PaymentLifecycleContext {

    private static final ThreadLocal<String> SOURCE = new ThreadLocal<>();

    private PaymentLifecycleContext() {
    }

    /** Sets the source for the current thread; always pair with {@link #clear()} in a finally block. */
    public static void source(String source) {
        SOURCE.set(source);
    }

    public static void clear() {
        SOURCE.remove();
    }

    /** The source set for this thread, or null when none was set. */
    static String currentSource() {
        return SOURCE.get();
    }
}
