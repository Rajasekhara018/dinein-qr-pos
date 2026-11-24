package com.heuristiq.search.indexing;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RedisStreamConfig {

    private final RedisConnectionFactory connectionFactory;
    private final RedisStreamProperties streamProperties;
    private final SearchIndexStreamListener listener;
    private final RedisStreamInitializer streamInitializer;

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> searchIndexStreamContainer() {
        streamInitializer.ensureConsumerGroupExists();
        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        Consumer consumer = Consumer.from(streamProperties.getConsumerGroup(), streamProperties.getConsumerName());
        // Not container.receive(..): its default cancels the subscription for good on the first error READING the stream (a
        // Redis timeout or a dropped connection). The service then ran on without consuming anything, the stream filled up to
        // its length cap and every event trimmed unread was lost to the index. A read error is now logged and the next read
        // follows after a short pause, so the subscription survives any Redis outage. Messages are still acknowledged only by
        // the listener, after they are indexed.
        StreamMessageListenerContainer.StreamReadRequest<String> request = StreamMessageListenerContainer.StreamReadRequest
                .builder(StreamOffset.create(streamProperties.getName(), ReadOffset.lastConsumed()))
                .consumer(consumer)
                .autoAcknowledge(false)
                .cancelOnError(error -> false)
                .errorHandler(RedisStreamConfig::onReadError)
                .build();
        container.register(request, listener);
        container.start();
        return container;
    }

    /** A failed read: logged, then a short pause so a Redis outage does not spin the polling thread (not a request thread). */
    static void onReadError(Throwable error) {
        log.warn("Reading the search index stream failed, retrying: {}", error.toString());
        try {
            Thread.sleep(IndexingConstants.STREAM_READ_ERROR_PAUSE);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
