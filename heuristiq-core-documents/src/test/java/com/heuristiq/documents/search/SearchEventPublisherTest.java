package com.railcab.dms.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchEventPublisherTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisConnection connection = mock(RedisConnection.class);
    private final RedisStreamCommands streams = mock(RedisStreamCommands.class);
    private final SearchEventPublisher publisher = new SearchEventPublisher(redis, new ObjectMapper());

    @SuppressWarnings("unchecked")
    SearchEventPublisherTest() {
        ReflectionTestUtils.setField(publisher, "streamName", "stream-x");
        ReflectionTestUtils.setField(publisher, "maxLength", 5000L);
        when(connection.streamCommands()).thenReturn(streams);
        // run the callback against the mocked connection, as the template would
        when(redis.execute(any(RedisCallback.class))).thenAnswer(inv -> ((RedisCallback<?>) inv.getArgument(0)).doInRedis(connection));
    }

    @SuppressWarnings("unchecked")
    private MapRecord<byte[], byte[], byte[]> lastRecord(ArgumentCaptor<XAddOptions> options) {
        ArgumentCaptor<MapRecord<byte[], byte[], byte[]>> record = ArgumentCaptor.forClass(MapRecord.class);
        verify(streams).xAdd(record.capture(), options.capture());
        return record.getValue();
    }

    private static Map<String, String> fields(MapRecord<byte[], byte[], byte[]> record) {
        Map<String, String> out = new HashMap<>();
        record.getValue().forEach((k, v) -> out.put(new String(k, StandardCharsets.UTF_8), new String(v, StandardCharsets.UTF_8)));
        return out;
    }

    @Test
    void indexWritesOperationModelKeyAndJsonPayloadAndCapsTheStream() {
        assertTrue(publisher.publishIndex("DOCUMENT", "d1", Map.of("name", "a")));

        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);
        MapRecord<byte[], byte[], byte[]> record = lastRecord(options);
        assertEquals("stream-x", new String(record.getStream(), StandardCharsets.UTF_8));
        Map<String, String> fields = fields(record);
        assertEquals("INDEX", fields.get(SearchConstants.FIELD_OPERATION));
        assertEquals("d1", fields.get(SearchConstants.FIELD_MODEL_KEY));
        assertEquals("{\"name\":\"a\"}", fields.get(SearchConstants.FIELD_PAYLOAD));
        assertEquals(5000L, options.getValue().getMaxlen());
        assertTrue(options.getValue().isApproximateTrimming());
    }

    @Test
    void indexFailureIsSwallowedAndReportedAsFalse() {
        when(streams.xAdd(any(MapRecord.class), any(XAddOptions.class))).thenThrow(new IllegalStateException("redis down"));
        assertFalse(publisher.publishIndex("DOCUMENT", "d1", Map.of()));
    }

    @Test
    void deleteWritesDeleteRecordAndNeverThrows() {
        publisher.publishDelete("DOCUMENT", "d1");
        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);
        assertEquals("DELETE", fields(lastRecord(options)).get(SearchConstants.FIELD_OPERATION));
        assertTrue(options.getValue().isApproximateTrimming());

        when(streams.xAdd(any(MapRecord.class), any(XAddOptions.class))).thenThrow(new IllegalStateException("redis down"));
        publisher.publishDelete("DOCUMENT", "d2");
    }
}
