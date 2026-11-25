package com.heuristiq.search.scope;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.elastic.ElasticController;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchQuery;
import com.heuristiq.search.security.GatewayAuthFilter;
import com.heuristiq.search.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Loads the actual dev profile used by every Search API Jenkins deployment, rather than hardcoded defaults. */
class DeployedProfileTenantIsolationTest {

    private final SearchIndexService index = mock(SearchIndexService.class);

    private TenantScopingProperties deployedProperties() {
        AtomicReference<TenantScopingProperties> found = new AtomicReference<>();
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=dev")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> found.set(Binder.get(context.getEnvironment())
                        .bind("railcab.search.tenant-scoping", TenantScopingProperties.class).get()));
        return found.get();
    }

    private MockMvc mvc() {
        TenantScopedSearch search = new TenantScopedSearch(index, new CurrentCaller(), new TenantScopePolicy(deployedProperties()));
        when(index.search(any(), any())).thenAnswer(call ->
                new PageImpl<SearchDocument>(List.of(), call.<Pageable>getArgument(1), 0));
        return MockMvcBuilders.standaloneSetup(new ElasticController(search))
                .addFilters(new GatewayAuthFilter("test-forward-key")).build();
    }

    @Test
    void theDeployedProfileKeepsTenantScopingEnabled() {
        assertThat(deployedProperties().enabled()).isTrue();
    }

    @Test
    void theGatewayOrganizationReachesTheRoleIndexQueryUnderTheDeployedProfile() throws Exception {
        MockMvc mvc = mvc();
        for (String tenant : List.of("org-a", "org-b")) {
            mvc.perform(post("/api/v1/search/elastic/ROLE/getall")
                            .header("X-Gateway-Token", "test-forward-key")
                            .header("X-User-Id", "user-" + tenant)
                            .header("X-User-Role", "ADMIN")
                            .header("X-Tenant-Id", tenant)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"requestType\":\"LIST\"}"))
                    .andExpect(status().isOk());
        }

        ArgumentCaptor<SearchQuery> queries = ArgumentCaptor.forClass(SearchQuery.class);
        verify(index, times(2)).search(queries.capture(), any());
        for (int i = 0; i < 2; i++) {
            String tenant = List.of("org-a", "org-b").get(i);
            assertThat(queries.getAllValues().get(i).group()).isNotNull();
            var scope = queries.getAllValues().get(i).group().groups().get(0);
            assertThat(scope.conditions()).singleElement().satisfies(condition -> {
                assertThat(condition.field()).isEqualTo("ownerKey");
                assertThat(condition.operator()).isEqualTo(Operator.EQ);
                assertThat(condition.value()).isEqualTo(tenant);
            });
        }
    }

    @Test
    void aUserMissingAnOrganizationCannotFallThroughToAnUnfilteredRoleQuery() throws Exception {
        mvc().perform(post("/api/v1/search/elastic/ROLE/getall")
                        .header("X-Gateway-Token", "test-forward-key")
                        .header("X-User-Id", "user-no-org")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requestType\":\"LIST\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(index);
    }
}
