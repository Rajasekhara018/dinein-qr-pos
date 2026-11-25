package com.heuristiq.registry.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The correlation id of a request is reused when sent, generated when not, visible while the request runs, and never leaks after it. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private MockHttpServletResponse run(String headerValue, AtomicReference<String> seenInChain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, headerValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> seenInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void anIncomingIdIsKeptAndEchoedOnTheResponse() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        MockHttpServletResponse response = run("abc-123", seen);

        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void aMissingIdIsReplacedByAGeneratedUuid() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        MockHttpServletResponse response = run(null, seen);

        String generated = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(seen.get()).isEqualTo(generated);
    }

    @Test
    void aBlankIdIsTreatedAsMissing() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        MockHttpServletResponse response = run("   ", seen);

        String id = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(id).isNotBlank();
        assertThat(seen.get()).isEqualTo(id);
    }

    @Test
    void theIdIsRemovedFromTheLogContextAfterTheRequest() throws Exception {
        run("abc-123", new AtomicReference<>());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theIdIsRemovedEvenWhenTheChainFails() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("downstream failed");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
