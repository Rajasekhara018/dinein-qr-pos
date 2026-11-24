package com.heuristiq.uam.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalServiceAuthenticationFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void matchingTokenAuthenticatesAsTheInternalService() throws Exception {
        run(new InternalServiceAuthenticationFilter("s3cret"), "s3cret");

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString)
                .containsExactly(SecurityConstants.SERVICE_AUTHORITY);
    }

    @Test
    void wrongOrMissingTokenLeavesTheRequestUnauthenticated() throws Exception {
        run(new InternalServiceAuthenticationFilter("s3cret"), "other");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        run(new InternalServiceAuthenticationFilter("s3cret"), null);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankConfiguredSecretRejectsEveryCaller() throws Exception {
        run(new InternalServiceAuthenticationFilter(""), "");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        run(new InternalServiceAuthenticationFilter(null), "anything");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void run(InternalServiceAuthenticationFilter filter, String suppliedToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (suppliedToken != null) {
            request.addHeader(SecurityConstants.INTERNAL_SERVICE_TOKEN_HEADER, suppliedToken);
        }
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
