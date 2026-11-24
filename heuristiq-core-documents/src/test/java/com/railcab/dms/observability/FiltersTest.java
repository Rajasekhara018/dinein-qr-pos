package com.railcab.dms.observability;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.security.DmsPrincipal;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FiltersTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void correlationIdIsGeneratedWhenMissingAndKeptWhenSupplied() throws Exception {
        CorrelationIdFilter f = new CorrelationIdFilter();
        AtomicReference<String> inChain = new AtomicReference<>();
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(new MockHttpServletRequest(), res, (a, b) -> inChain.set(MDC.get(DmsCommonConstants.MDC_KEY)));
        assertNotNull(inChain.get());
        assertEquals(inChain.get(), res.getHeader(DmsCommonConstants.CORRELATION_ID_HEADER));
        assertNull(MDC.get(DmsCommonConstants.MDC_KEY));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(DmsCommonConstants.CORRELATION_ID_HEADER, "corr-1");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        f.doFilter(req, res2, (a, b) -> inChain.set(MDC.get(DmsCommonConstants.MDC_KEY)));
        assertEquals("corr-1", inChain.get());
        assertEquals("corr-1", res2.getHeader(DmsCommonConstants.CORRELATION_ID_HEADER));
    }

    @Test
    void correlationMdcIsRemovedEvenWhenChainFails() {
        CorrelationIdFilter f = new CorrelationIdFilter();
        assertThrows(ServletException.class, () -> f.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (a, b) -> {
                    throw new ServletException("boom");
                }));
        assertNull(MDC.get(DmsCommonConstants.MDC_KEY));
    }

    @Test
    void accessLogPassesRequestThroughWithAndWithoutPrincipal() throws Exception {
        AccessLogFilter f = new AccessLogFilter();
        AtomicBoolean called = new AtomicBoolean(false);
        f.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), (a, b) -> called.set(true));
        assertTrue(called.get());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new DmsPrincipal("u1", "ADMIN"), null));
        called.set(false);
        f.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), (a, b) -> called.set(true));
        assertTrue(called.get());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("plain", null));
        called.set(false);
        f.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), (a, b) -> called.set(true));
        assertTrue(called.get());
    }

    @Test
    void accessLogPropagatesChainFailure() {
        AccessLogFilter f = new AccessLogFilter();
        assertThrows(ServletException.class, () -> f.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (a, b) -> {
                    throw new ServletException("boom");
                }));
    }
}
