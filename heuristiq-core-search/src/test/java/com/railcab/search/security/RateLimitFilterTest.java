package com.heuristiq.search.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The search rate limiter counts and starts its window in one atomic Redis script call. */
class RateLimitFilterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RateLimitProperties properties = new RateLimitProperties();
    private final RateLimitFilter filter = new RateLimitFilter(redis, properties);
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        properties.setApi(new RateLimitProperties.EndpointLimit(2, 60));
    }

    @SuppressWarnings("unchecked")
    private void counterReturns(long count) {
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(count);
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsWithOneAtomicScriptKeyedByUserAndWindow() throws Exception {
        counterReturns(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SearchSecurityConstants.USER_ID_HEADER, "u1");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), eq(List.of("search:ratelimit:api:user:u1")), eq("60"));
        assertThat(script.getValue().getScriptAsString()).contains("INCR").contains("EXPIRE").contains("TTL");
        assertThat(script.getValue().getResultType()).isEqualTo(Long.class);
        verify(redis, never()).opsForValue();
        verify(redis, never()).expire(anyString(), any(java.time.Duration.class));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void overTheLimitIsRejectedWith429() throws Exception {
        counterReturns(3L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void atTheLimitIsAllowed() throws Exception {
        counterReturns(2L);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void disabledLimiterNeverTouchesRedis() throws Exception {
        properties.setEnabled(false);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        verifyNoInteractions(redis);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void authenticatedSourcePublicationDoesNotConsumeBrowserQuotas() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(filter,"projectionServiceToken","source-secret");
        var request=new MockHttpServletRequest("POST","/api/v1/search/internal/projections");
        request.setServletPath("/api/v1/search/internal/projections");
        request.addHeader("X-Rider-Service-Token","source-secret");
        filter.doFilter(request,new MockHttpServletResponse(),chain);
        verifyNoInteractions(redis);verify(chain).doFilter(any(),any());
    }

    @Test
    void aWrongSourceTokenCannotBypassTheLimiter() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(filter,"projectionServiceToken","source-secret");
        counterReturns(3);
        var request=new MockHttpServletRequest("POST","/api/v1/search/internal/projections");
        request.setServletPath("/api/v1/search/internal/projections");request.addHeader("X-Rider-Service-Token","wrong");
        var response=new MockHttpServletResponse();filter.doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(429);verifyNoInteractions(chain);
    }

    @Test
    void aServiceTokenCannotExemptAnOrdinarySearchRequest() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(filter,"projectionServiceToken","source-secret");
        counterReturns(3);
        var request=new MockHttpServletRequest("POST","/api/v1/search/elastic/WEB_ADMIN_DRIVER/getall");
        request.setServletPath("/api/v1/search/elastic/WEB_ADMIN_DRIVER/getall");request.addHeader("X-Rider-Service-Token","source-secret");
        var response=new MockHttpServletResponse();filter.doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(429);verifyNoInteractions(chain);
    }
}
