package com.heuristiq.search.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A failed read of the search index stream is logged and paused, never thrown, so the subscription keeps reading. */
class RedisStreamConfigReadErrorTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void aReadErrorIsSwallowedAfterAShortPause() {
        long started = System.nanoTime();

        assertThatCode(() -> RedisStreamConfig.onReadError(new IllegalStateException("Redis down"))).doesNotThrowAnyException();

        assertThat(System.nanoTime() - started).isGreaterThanOrEqualTo(IndexingConstants.STREAM_READ_ERROR_PAUSE.toNanos() / 2);
    }

    @Test
    void anInterruptDuringThePauseIsKeptForTheContainerToStop() {
        Thread.currentThread().interrupt();

        RedisStreamConfig.onReadError(new IllegalStateException("Redis down"));

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
