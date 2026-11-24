package com.heuristiq.search.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.search.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes index/delete events off the shared Redis Stream. Mirrors touidx's
 * {@code SearchListener}/{@code IdxSingle} intent (async, at-least-once, consumer-group-based) but
 * as a single stream/listener rather than touidx's three parallel streams
 * (standard/domain/control) with horizontal sharding across {@code SearchListenerDom2}/{@code Dom3}
 * -- RailCab's write volume doesn't need that.
 *
 * <p>Uses {@code MapRecord<String, String, String>} throughout (rather than {@code Object}
 * hash-key/value types) since Redis Stream field values are always strings on the wire -- matching
 * what {@link org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions#builder()}
 * resolves to by default.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final SearchIndexService searchIndexService;
    private final SearchDocumentAssembler documentAssembler;
    private final RedisStreamProperties streamProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            process(message);
            acknowledge(message.getId());
        } catch (Exception ex) {
            // Deliberately not acked -- stays pending and is retried by PendingEntriesRecoveryJob.
            log.error("Failed to process search index event [{}]: {}", message.getId(), ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    void process(MapRecord<String, String, String> message) throws Exception {
        Map<String, String> fields = message.getValue();
        String operation = fields.get(IndexingConstants.FIELD_OPERATION);
        String model = fields.get(IndexingConstants.FIELD_MODEL);
        String modelKey = fields.get(IndexingConstants.FIELD_MODEL_KEY);
        String rawPayload = fields.get(IndexingConstants.FIELD_PAYLOAD);

        if (IndexingConstants.OPERATION_DELETE.equals(operation)) {
            searchIndexService.delete(model, modelKey);
            return;
        }
        if (!IndexingConstants.OPERATION_INDEX.equals(operation)) {
            log.warn("Ignoring search index event [{}] with unknown operation [{}]", message.getId(), operation);
            return;
        }
        Map<String, Object> payload = rawPayload == null || rawPayload.isBlank()
                ? Map.of()
                : objectMapper.readerFor(Map.class).with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(rawPayload);
        searchIndexService.index(documentAssembler.assemble(model, modelKey, payload));
    }

    private void acknowledge(RecordId id) {
        redisTemplate.<String, String>opsForStream()
                .acknowledge(streamProperties.getName(), streamProperties.getConsumerGroup(), id);
    }

    Consumer consumer() {
        return Consumer.from(streamProperties.getConsumerGroup(), streamProperties.getConsumerName());
    }
}
