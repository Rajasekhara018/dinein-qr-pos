package com.heuristiq.audit.adapter.in.web;

import com.heuristiq.audit.application.AuditQueryService;
import com.heuristiq.audit.application.CallerIdentity;
import com.heuristiq.audit.application.EventQuery;
import com.heuristiq.audit.domain.AuditException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MyActivityControllerTest {

    private final AuditQueryService queries = mock(AuditQueryService.class);
    private final MyActivityController controller = new MyActivityController(queries);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void alwaysFiltersByTheTokenUserAndCapsThePageSize() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user-42", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        controller.mine("ACCESS", null, 0, 5000);

        ArgumentCaptor<EventQuery> query = ArgumentCaptor.forClass(EventQuery.class);
        verify(queries).search(any(CallerIdentity.class), query.capture(), eq(0), eq(WebConstants.MY_EVENTS_MAX_PAGE_SIZE));
        assertEquals("user-42", query.getValue().userId());
        assertEquals("ACCESS", query.getValue().eventType());
    }

    @Test
    void refusesAnAnonymousCaller() {
        assertThrows(AuditException.class, () -> controller.mine(null, null, 0, 20));
        verifyNoInteractions(queries);
    }
}
