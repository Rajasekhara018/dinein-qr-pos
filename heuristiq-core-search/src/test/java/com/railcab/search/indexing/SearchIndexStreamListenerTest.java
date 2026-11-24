package com.heuristiq.search.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.service.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexStreamListenerTest {

    @Mock
    private SearchIndexService searchIndexService;
    @Mock
    private SearchDocumentAssembler documentAssembler;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private StreamOperations<String, String, String> streamOperations;

    private RedisStreamProperties streamProperties;
    private SearchIndexStreamListener listener;

    @BeforeEach
    void setUp() {
        streamProperties = new RedisStreamProperties();
        lenient().when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);
        listener = new SearchIndexStreamListener(searchIndexService, documentAssembler, streamProperties,
                redisTemplate, new ObjectMapper());
    }

    private MapRecord<String, String, String> recordWith(Map<String, String> fields) {
        @SuppressWarnings("unchecked")
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getValue()).thenReturn(fields);
        lenient().when(record.getId()).thenReturn(RecordId.of("1-1"));
        return record;
    }

    @Test
    void indexesDocumentAndAcknowledgesOnIndexOperation() {
        Map<String, String> fields = new HashMap<>();
        fields.put("operation", "INDEX");
        fields.put("model", "RIDE");
        fields.put("modelKey", "ride-1");
        fields.put("payload", "{\"text1\":\"MG Road\"}");
        MapRecord<String, String, String> record = recordWith(fields);

        SearchDocument document = new SearchDocument();
        when(documentAssembler.assemble(eq("RIDE"), eq("ride-1"), any())).thenReturn(document);

        listener.onMessage(record);

        verify(searchIndexService).index(document);
        verify(streamOperations).acknowledge(streamProperties.getName(), streamProperties.getConsumerGroup(), record.getId());
    }

    @Test
    void deletesDocumentWithoutTouchingAssemblerOnDeleteOperation() {
        Map<String, String> fields = new HashMap<>();
        fields.put("operation", "DELETE");
        fields.put("model", "RIDE");
        fields.put("modelKey", "ride-1");
        MapRecord<String, String, String> record = recordWith(fields);

        listener.onMessage(record);

        verify(searchIndexService).delete("RIDE", "ride-1");
        verifyNoInteractions(documentAssembler);
        verify(streamOperations).acknowledge(streamProperties.getName(), streamProperties.getConsumerGroup(), record.getId());
    }

    @Test
    void doesNotAcknowledgeWhenIndexingFails() {
        Map<String, String> fields = new HashMap<>();
        fields.put("operation", "INDEX");
        fields.put("model", "RIDE");
        fields.put("modelKey", "ride-1");
        fields.put("payload", "{}");
        MapRecord<String, String, String> record = recordWith(fields);
        when(documentAssembler.assemble(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        listener.onMessage(record);

        verify(streamOperations, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void ignoresUnknownOperation() throws Exception {
        Map<String, String> fields = new HashMap<>();
        fields.put("operation", "PURGE");
        fields.put("model", "RIDE");
        fields.put("modelKey", "ride-1");
        MapRecord<String, String, String> record = recordWith(fields);

        listener.process(record);

        verifyNoInteractions(searchIndexService, documentAssembler);
    }
}
