package com.heuristiq.notification.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStreamCommands.MaxLenTrimStrategy;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOperator;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every search event is added with an approximate MAXLEN, so the shared stream cannot grow forever. */
class SearchEventPublisherStreamCapTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void indexAndDeleteEventsCapTheStreamApproximately() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations ops = mock(StreamOperations.class);
        doReturn(ops).when(redis).opsForStream();
        SearchEventPublisher publisher = new SearchEventPublisher(redis, new ObjectMapper());
        ReflectionTestUtils.setField(publisher, "streamName", "stream-x");
        ReflectionTestUtils.setField(publisher, "maxLength", 2500L);

        assertThat(publisher.publishIndex("MODEL", "k1", Map.of("a", "b"))).isTrue();
        publisher.publishDelete("MODEL", "k1");

        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);
        verify(ops, times(2)).add(eq("stream-x"), anyMap(), options.capture());
        assertThat(options.getAllValues()).allSatisfy(o -> {
            assertThat(o.hasTrimOptions()).isTrue();
            assertThat(o.getTrimOptions().getTrimOperator()).isEqualTo(TrimOperator.APPROXIMATE);
            assertThat(o.getTrimOptions().getTrimStrategy()).isInstanceOfSatisfying(MaxLenTrimStrategy.class,
                    strategy -> assertThat(strategy.threshold()).isEqualTo(2500L));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void aRedisFailureIsReportedNotThrown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations ops = mock(StreamOperations.class);
        doReturn(ops).when(redis).opsForStream();
        when(ops.add(anyString(), anyMap(), org.mockito.ArgumentMatchers.any(XAddOptions.class)))
                .thenThrow(new IllegalStateException("redis down"));
        SearchEventPublisher publisher = new SearchEventPublisher(redis, new ObjectMapper());
        ReflectionTestUtils.setField(publisher, "streamName", "stream-x");

        assertThat(publisher.publishIndex("MODEL", "k1", Map.of())).isFalse();
        publisher.publishDelete("MODEL", "k1");
    }
}
