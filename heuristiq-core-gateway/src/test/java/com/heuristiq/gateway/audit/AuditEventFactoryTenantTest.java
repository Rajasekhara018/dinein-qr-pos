package com.heuristiq.gateway.audit;

import com.heuristiq.gateway.common.constants.GatewayConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** An ACCESS event carries the organization of the verified session, so the audit screen can separate organizations. */
class AuditEventFactoryTenantTest {

    private final AuditProperties props = new AuditProperties();
    private final AuditEventFactory factory = new AuditEventFactory(props, new AuditPathRules(props));

    private GatewayAuditEvent eventFor(String tenantHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.method(HttpMethod.GET, "/api/rider/config/config/settings/42")
                .header(GatewayConstants.X_USER_ID, "u-42").header(GatewayConstants.X_USER_ROLE, "ADMIN");
        if (tenantHeader != null) {
            request.header(GatewayConstants.X_TENANT_ID, tenantHeader);
        }
        return factory.build(MockServerWebExchange.from(request.build()), 200, Instant.parse("2026-10-15T00:00:00Z"), 12, null);
    }

    @Test
    void theEventCarriesTheOrganizationTheGatewayForwardedFromTheToken() {
        assertEquals("org-7", eventFor("org-7").getTenantId());
    }

    @Test
    void aSessionWithoutAnOrganizationLeavesItNull() {
        assertNull(eventFor(null).getTenantId());
        assertNull(eventFor("  ").getTenantId());
    }
}
