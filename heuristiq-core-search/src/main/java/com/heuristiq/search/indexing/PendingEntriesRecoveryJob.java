package com.heuristiq.search.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Recovers messages left unacknowledged by a crashed/restarted consumer -- Spring Data Redis's
 * standard pending-entries-list (XPENDING) + claim (XCLAIM) API, rather than touidx's hand-rolled
 * retry logic. Runs on a fixed delay; any entry idle longer than
 * {@code railcab.search.stream.min-idle-time-ms} is reclaimed by this instance and reprocessed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingEntriesRecoveryJob {

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties streamProperties;
    private final SearchIndexStreamListener listener;

    @Scheduled(fixedDelayString = "${railcab.search.stream.recovery-interval-ms:60000}")
    public void reclaimStalePendingEntries() {
        Duration minIdleTime = Duration.ofMillis(streamProperties.getMinIdleTimeMs());
        Consumer consumer = listener.consumer();

        PendingMessages pending;
        try {
            pending = redisTemplate.opsForStream()
                    .pending(streamProperties.getName(), consumer, Range.unbounded(), 50);
        } catch (Exception ex) {
            log.warn("Could not read pending entries for stream [{}]: {}", streamProperties.getName(), ex.getMessage());
            return;
        }
        if (pending == null || pending.isEmpty()) {
            return;
        }

        List<RecordId> staleIds = pending.stream()
                .filter(message -> message.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) >= 0)
                .map(PendingMessage::getId)
                .toList();
        if (staleIds.isEmpty()) {
            return;
        }

        log.info("Reclaiming {} stale pending entries on stream [{}]", staleIds.size(), streamProperties.getName());
        List<MapRecord<String, String, String>> claimed = redisTemplate.<String, String>opsForStream().claim(
                streamProperties.getName(), streamProperties.getConsumerGroup(), streamProperties.getConsumerName(),
                XClaimOptions.minIdle(minIdleTime).ids(staleIds.toArray(new RecordId[0])));

        for (MapRecord<String, String, String> record : claimed) {
            try {
                listener.process(record);
                redisTemplate.<String, String>opsForStream()
                        .acknowledge(streamProperties.getName(), streamProperties.getConsumerGroup(), record.getId());
            } catch (Exception ex) {
                log.error("Reclaimed entry [{}] failed again: {}", record.getId(), ex.getMessage(), ex);
            }
        }
    }
}
