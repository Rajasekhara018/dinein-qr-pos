package com.heuristiq.notification.infrastructure.config;

import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** NMS must come up, and keep trying to attach its Kafka listeners, while the broker is unreachable. */
class KafkaListenerStarterTest {

    private static final long WAIT_SECONDS = 5;

    private final KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);

    private KafkaListenerStarter starter(MessageListenerContainer... containers) {
        when(registry.getListenerContainers()).thenReturn(List.of(containers));
        return new KafkaListenerStarter(registry, 5, 20);
    }

    private static MessageListenerContainer container(boolean running) {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(running);
        return container;
    }

    @Test
    void startsEveryListenerThatIsNotRunningAndSkipsTheOthers() throws InterruptedException {
        MessageListenerContainer idle = container(false);
        MessageListenerContainer alreadyUp = container(true);
        CountDownLatch started = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            return null;
        }).when(idle).start();

        KafkaListenerStarter starter = starter(idle, alreadyUp);
        starter.start();

        assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS), "the idle listener was never started");
        verify(idle).start();
        verify(alreadyUp, never()).start();
        starter.stop();
    }

    @Test
    void keepsRetryingWhileTheBrokerCannotBeReachedThenStops() throws InterruptedException {
        MessageListenerContainer container = container(false);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch succeeded = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new KafkaException("No resolvable bootstrap urls given in bootstrap.servers");
            }
            succeeded.countDown();
            return null;
        }).when(container).start();

        KafkaListenerStarter starter = starter(container);
        starter.start();

        assertTrue(succeeded.await(WAIT_SECONDS, TimeUnit.SECONDS), "never recovered once the broker was back");
        Thread.sleep(100);
        assertEquals(3, attempts.get(), "must stop retrying once the listener is running");
        starter.stop();
    }

    @Test
    void stoppingEndsTheRetries() throws InterruptedException {
        MessageListenerContainer container = container(false);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new KafkaException("broker down");
        }).when(container).start();

        KafkaListenerStarter starter = starter(container);
        starter.start();
        while (attempts.get() < 2) {
            Thread.sleep(5);
        }
        starter.stop();
        Thread.sleep(100);
        int afterStop = attempts.get();
        Thread.sleep(150);

        assertEquals(afterStop, attempts.get(), "retries went on after stop()");
        assertFalse(starter.isRunning());
    }

    @Test
    void startingTwiceDoesNotStartASecondWorker() throws InterruptedException {
        MessageListenerContainer container = container(false);
        AtomicInteger starts = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        doAnswer(invocation -> {
            starts.incrementAndGet();
            started.countDown();
            return null;
        }).when(container).start();

        KafkaListenerStarter starter = starter(container);
        starter.start();
        starter.start();

        assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(1, starts.get());
        starter.stop();
    }

    @Test
    void anApplicationWithNoListenersStartsCleanly() throws InterruptedException {
        KafkaListenerStarter starter = starter();
        starter.start();
        Thread.sleep(50);
        assertTrue(starter.isRunning());
        starter.stop();
        assertFalse(starter.isRunning());
    }

    @Test
    void startsLastSoTheWebServerIsAlreadyAnswering() {
        assertEquals(Integer.MAX_VALUE, starter().getPhase());
    }
}
