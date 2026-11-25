package com.heuristiq.notification.infrastructure.config;

/** Fixed values of {@link KafkaListenerStarter}. What may differ per environment is read from {@code nms.kafka.listener.*}. */
public final class KafkaListenerStartConstants {

    /** Name of the background thread that attaches the Kafka listeners. */
    public static final String STARTER_THREAD_NAME = "nms-kafka-listener-starter";

    /** Default wait before the first retry, in milliseconds; it doubles after each failure. */
    public static final String DEFAULT_RETRY_INITIAL_MS = "2000";

    /** Default longest wait between two retries, in milliseconds. */
    public static final String DEFAULT_RETRY_MAX_MS = "60000";

    /** Lifecycle phase: after every other bean has started, so the web server is already answering. */
    public static final int LIFECYCLE_PHASE = Integer.MAX_VALUE;

    private KafkaListenerStartConstants() {
    }
}
