package com.railcab.nms.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes index/delete events onto the shared Redis Stream that 2026_RailCab_SearchAPI consumes.
 * Copy-pasted per producer service (BookingsAPI/AdminAPI/UAM/DMS have their own) rather than a
 * shared library, matching this platform's established convention. Fire-and-forget: a publish
 * failure is logged, not thrown -- search indexing must never block or fail a template operation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchEventPublisher {

    @Value("${railcab.search.stream.name:railcab:search:index-stream}")
    private String streamName;

    /**
     * Approximate cap on the shared stream's length (XADD MAXLEN ~). Without it the stream grows forever, because
     * entries the search service has already read are never removed. Set it well above any backlog the search
     * service may fall behind by: entries trimmed before they are read are lost to the index until a reindex.
     */
    @Value("${railcab.search.stream.max-length:100000}")
    private long maxLength;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Publishes an index event; returns {@code true} when the stream accepted it, {@code false} when it did not (logged). */
    public boolean publishIndex(String model, String modelKey, Map<String, Object> fields) {
        try {
            Map<String, String> record = new HashMap<>();
            record.put(SearchConstants.FIELD_OPERATION, SearchOperation.INDEX.name());
            record.put(SearchConstants.FIELD_MODEL, model);
            record.put(SearchConstants.FIELD_MODEL_KEY, modelKey);
            record.put(SearchConstants.FIELD_PAYLOAD, objectMapper.writeValueAsString(fields));
            redisTemplate.<String, String>opsForStream().add(streamName, record, trimmed());
            return true;
        } catch (Exception ex) {
            log.warn("Failed to publish search index event for {}:{} - {}", model, modelKey, ex.getMessage());
            return false;
        }
    }

    public void publishDelete(String model, String modelKey) {
        try {
            Map<String, String> record = Map.of(SearchConstants.FIELD_OPERATION, SearchOperation.DELETE.name(),
                    SearchConstants.FIELD_MODEL, model, SearchConstants.FIELD_MODEL_KEY, modelKey);
            redisTemplate.<String, String>opsForStream().add(streamName, record, trimmed());
        } catch (Exception ex) {
            log.warn("Failed to publish search delete event for {}:{} - {}", model, modelKey, ex.getMessage());
        }
    }

    /** XADD with MAXLEN ~ maxLength: Redis trims whole macro nodes only, so the cap costs next to nothing per add. */
    private XAddOptions trimmed() {
        return XAddOptions.trim(TrimOptions.maxLen(maxLength).approximate());
    }
}
