package com.heuristiq.resilient;

/** Whether repeating a call is safe. Only {@link #SAFE} calls are ever retried. */
public enum Idempotency {
    /** GETs, or writes that carry an idempotency key the callee honours. Retried on transient failure. */
    SAFE,
    /** Creates or changes state with no key. Never retried: a timeout may already have committed. */
    UNSAFE
}
