package com.railcab.nms.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Attaches the Kafka listeners in the background, after the application is up, and keeps trying until the broker is reachable.
 *
 * <p>Spring would otherwise start the listeners while the application starts, and a broker that cannot be resolved ("No
 * resolvable bootstrap urls") then fails the whole application. NMS answers REST calls that do not need Kafka at all, notably
 * the one-time codes UAM emails at sign-up, so a missing broker must not take those down: the container factory is set to start
 * manually ({@link KafkaConfig}), NMS comes up, and this component retries with a growing delay (doubling up to a ceiling) until
 * every listener is running, then stops. A failure is logged as a warning each time, so it stays visible.
 */
@Component
public class KafkaListenerStarter implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerStarter.class);

    private final KafkaListenerEndpointRegistry registry;
    private final long initialDelayMs;
    private final long maxDelayMs;

    private volatile boolean running;
    private volatile Thread worker;

    public KafkaListenerStarter(
            KafkaListenerEndpointRegistry registry,
            @Value("${nms.kafka.listener.start-retry-initial-ms:" + KafkaListenerStartConstants.DEFAULT_RETRY_INITIAL_MS + "}")
            long initialDelayMs,
            @Value("${nms.kafka.listener.start-retry-max-ms:" + KafkaListenerStartConstants.DEFAULT_RETRY_MAX_MS + "}")
            long maxDelayMs) {
        this.registry = registry;
        this.initialDelayMs = Math.max(1, initialDelayMs);
        this.maxDelayMs = Math.max(this.initialDelayMs, maxDelayMs);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(this::attachListeners, KafkaListenerStartConstants.STARTER_THREAD_NAME);
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return KafkaListenerStartConstants.LIFECYCLE_PHASE;
    }

    private void attachListeners() {
        long delay = initialDelayMs;
        while (running) {
            try {
                startStoppedListeners();
                log.info("Kafka listeners are running");
                return;
            } catch (RuntimeException e) {
                log.warn("Kafka listeners could not start ({}); NMS keeps serving requests, retrying in {} ms",
                        e.getMessage(), delay);
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            delay = Math.min(delay * 2, maxDelayMs);
        }
    }

    private void startStoppedListeners() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            if (!container.isRunning()) {
                container.start();
            }
        }
    }
}
