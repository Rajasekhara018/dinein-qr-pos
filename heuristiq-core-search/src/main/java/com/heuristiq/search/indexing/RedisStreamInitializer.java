package com.heuristiq.search.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures the shared stream + consumer group exist. Called synchronously by
 * {@link RedisStreamConfig} before the listener container starts receiving -- must happen
 * before the first XREADGROUP, not as a separate post-startup ApplicationRunner, otherwise the
 * container's polling thread could race the group's creation on first boot. Mirrors touidx's
 * {@code initStream}/{@code createConsumerGroup} (in {@code AbstractStreamSingle}) for a single
 * stream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisStreamInitializer {

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties streamProperties;

    public void ensureConsumerGroupExists() {
        try {
            redisTemplate.opsForStream().createGroup(streamProperties.getName(), ReadOffset.from("0"),
                    streamProperties.getConsumerGroup());
            log.info("Created Redis Stream consumer group [{}] on stream [{}]",
                    streamProperties.getConsumerGroup(), streamProperties.getName());
        } catch (RedisSystemException ex) {
            // Spring wraps the driver exception with a generic message ("Error in execution") --
            // the actual "BUSYGROUP" text is only on the Lettuce cause, not ex.getMessage() itself.
            Throwable rootCause = ex.getMostSpecificCause();
            if (rootCause.getMessage() != null && rootCause.getMessage().contains(IndexingConstants.BUSYGROUP_ERROR)) {
                log.debug("Redis Stream consumer group [{}] already exists on stream [{}]",
                        streamProperties.getConsumerGroup(), streamProperties.getName());
            } else {
                throw ex;
            }
        }
    }
}
