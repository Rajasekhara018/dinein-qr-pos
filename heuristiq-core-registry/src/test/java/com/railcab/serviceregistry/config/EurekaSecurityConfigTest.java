package com.railcab.serviceregistry.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The registry boots with its real security chain: the Eureka API and dashboard need the shared credential, while health
 * checks, metrics scraping and the packaged javadoc stay public.
 */
@SpringBootTest
@ActiveProfiles("test")
class EurekaSecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(CorrelationIdFilter.class))
                .apply(springSecurity()).build();
    }

    @Test
    void theEurekaApiIsRefusedWithoutCredentials() throws Exception {
        mvc.perform(get("/eureka/apps")).andExpect(status().isUnauthorized());
    }

    @Test
    void theEurekaApiIsRefusedWithAWrongPassword() throws Exception {
        mvc.perform(get("/eureka/apps").with(httpBasic("registry-test", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void theDashboardAnswersWithTheSharedCredential() throws Exception {
        // The Eureka REST API itself is served by a servlet filter that MockMvc does not run, so the dashboard page stands in for it.
        mvc.perform(get("/").with(httpBasic("registry-test", "registry-test-secret"))).andExpect(status().isOk());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void theJavadocPathIsNotBehindTheLogin() throws Exception {
        // Nothing is packaged in a test run, so the answer is 404 (not found), never 401 (login required).
        mvc.perform(get("/javadoc/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }
}
