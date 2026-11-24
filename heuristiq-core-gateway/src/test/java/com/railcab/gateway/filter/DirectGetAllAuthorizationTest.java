package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectGetAllAuthorizationTest {
    @Test
    void everyCataloguedPostMapsOnlyToItsOwnSourceRead() throws Exception {
        try (var in = getClass().getResourceAsStream("/direct-getall-routes.json")) {
            for (var row : new ObjectMapper().readTree(in)) {
                String source = row.path("path").asText().replaceAll("\\{[^}]+}", "record-1");
                String service = row.path("service").asText();
                assertEquals(source, DirectGetAllPaths.source(source + "/getall", HttpMethod.POST, service));
                assertNull(DirectGetAllPaths.source(source + "/getall", HttpMethod.GET, service));
                assertNull(DirectGetAllPaths.source(source + "/getall", HttpMethod.POST, "other"));
            }
        }
        assertNull(DirectGetAllPaths.source("/api/v1/wallet/admin/journals/id/reverse/getall", HttpMethod.POST, "wallet"));
    }

    @Test
    void readGrantAllowsGetAllButCreateGrantDoesNot() {
        for (String service : List.of("admin", "rider-wallet")) {
            String path = service.equals("admin") ? "/api/v1/admin/vehicles" : "/api/v1/wallet/admin/disputes";
            assertDecision(path, service, Set.of(10), true, List.of(
                    new PrivilegeEntry(10, path, service, "LIST"), new PrivilegeEntry(20, path, service, "CREATE")));
            assertDecision(path, service, Set.of(20), false, List.of(
                    new PrivilegeEntry(10, path, service, "LIST"), new PrivilegeEntry(20, path, service, "CREATE")));
        }
    }

    @Test
    void aMappedReadWithoutASourceCatalogEntryFailsClosed() {
        assertDecision("/api/v1/wallet/admin/accounts", "rider-wallet", Set.of(10), false,
                List.of(new PrivilegeEntry(10, "/unrelated", "rider-wallet", "LIST")));
    }

    @Test
    void scopedAccountEntriesRequireTheEntryReadGrant() {
        assertDecision("/api/v1/wallet/admin/accounts/account-1/entries", "rider-wallet", Set.of(10), true,
                List.of(new PrivilegeEntry(10, "/api/v1/wallet/admin/accounts/{accountId}/entries", "rider-wallet", "LIST")));
    }

    private void assertDecision(String path, String service, Set<Integer> grants, boolean allowed, List<PrivilegeEntry> entries) {
        var loader = mock(PrivilegeCatalogLoader.class);
        var cache = mock(RolePrivilegeCacheService.class);
        var chain = mock(GatewayFilterChain.class);
        when(loader.load()).thenReturn(entries);
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(grants);
        if (allowed) when(chain.filter(any())).thenReturn(Mono.empty());
        var filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), loader, new PathMatchersConfig(), cache,
                new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path + "/getall")
                .header(GatewayConstants.X_USER_ROLES, "CUSTOM_ROLE").build());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        if (allowed) {
            assertNull(exchange.getResponse().getStatusCode());
            verify(chain).filter(any());
        } else {
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
            verify(chain, never()).filter(any());
        }
    }
}
