package com.heuristiq.notification.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.adapters.in.web.readiness.InternalReadinessController;
import com.heuristiq.notification.application.usecase.readiness.NmsReadinessService;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import com.heuristiq.notification.shared.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/nms/internal/readiness: only a service credential opens it (never the NMS API key the gateway adds to user
 * traffic), 400 on blank params in the standard envelope, and the contract's response shape.
 */
class NmsInternalReadinessEndpointTest {

    private static final String PATH = "/api/v1/nms/internal/readiness";
    private static final String JWT_SECRET = "unit-test-secret-unit-test-secret-unit-test-secret";
    private static final String SERVICE_TOKEN = "shared-service-token";
    private static final String INTERNAL_KEY = "internal-key";

    private ITemplateRepository templates;
    private JwtSupport jwtSupport;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        templates = mock(ITemplateRepository.class);
        jwtSupport = new JwtSupport();
        ReflectionTestUtils.setField(jwtSupport, "jwtSecret", JWT_SECRET);
        NmsReadinessService service = new NmsReadinessService(templates,
                Clock.fixed(Instant.parse("2026-10-26T10:15:30Z"), ZoneOffset.UTC), Duration.ofSeconds(2));
        mvc = MockMvcBuilders.standaloneSetup(new InternalReadinessController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filter(SERVICE_TOKEN))
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private NmsInternalServiceAuthFilter filter(String token) {
        return new NmsInternalServiceAuthFilter(new InternalServiceAuthProperties(token), jwtSupport,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private static String jwt(String role) {
        return "Bearer " + Jwts.builder().subject("rider-uam").claim("roles", List.of(role))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static MockHttpServletRequestBuilder request() {
        return get(PATH).servletPath(PATH).param("applicationCode", "PASUPU_KUMKUMA_MATRIMONY_APP").param("tenantId", "org-a");
    }

    @Test
    void noCredentialTheGatewayApiKeyAWrongTokenOrAPersonsTokenAre401AndNothingIsRead() throws Exception {
        mvc.perform(request()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(request().header("X-NMS-API-Key", INTERNAL_KEY)).andExpect(status().isUnauthorized());
        mvc.perform(request().header("X-Rider-Service-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(request().header("Authorization", jwt("NMS_ADMIN"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(templates);
    }

    @Test
    void aBlankConfiguredTokenNeverMatches() throws Exception {
        MockMvc noToken = MockMvcBuilders.standaloneSetup(new InternalReadinessController(
                        new NmsReadinessService(templates, Clock.systemUTC(), Duration.ofSeconds(2))))
                .addFilters(filter("")).build();
        noToken.perform(request().header("X-Rider-Service-Token", "")).andExpect(status().isUnauthorized());
        verifyNoInteractions(templates);
    }

    @Test
    void blankOrMissingParamsAre400InTheStandardEnvelope() throws Exception {
        mvc.perform(get(PATH).servletPath(PATH).param("applicationCode", " ").param("tenantId", "org-a")
                        .header("X-Rider-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("applicationCode and tenantId are required"));
        mvc.perform(get(PATH).servletPath(PATH).param("applicationCode", "PASUPU_KUMKUMA_MATRIMONY_APP")
                        .header("X-Rider-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(templates);
    }

    @Test
    void theServiceTokenOrAServiceJwtGetTheReportInTheContractShape() throws Exception {
        when(templates.findByActiveTrue()).thenReturn(List.of());

        mvc.perform(request().header("X-Rider-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.service").value("nms"))
                .andExpect(jsonPath("$.responseObject.applicationCode").value("PASUPU_KUMKUMA_MATRIMONY_APP"))
                .andExpect(jsonPath("$.responseObject.tenantId").value("org-a"))
                .andExpect(jsonPath("$.responseObject.evaluatedAt").value("2026-10-26T10:15:30Z"))
                .andExpect(jsonPath("$.responseObject.checks", hasSize(1)))
                .andExpect(jsonPath("$.responseObject.checks[0].id").value("NMS_TEMPLATES_EXIST"))
                .andExpect(jsonPath("$.responseObject.checks[0].status").value("FAIL"))
                .andExpect(jsonPath("$.responseObject.checks[0].messageKey").value("readiness.nms.templatesExist.fail"))
                .andExpect(jsonPath("$.responseObject.checks[0].params.templateCount").value("0"))
                .andExpect(jsonPath("$.responseObject.checks[0].fixPath")
                        .value("/nms-templates?application=PASUPU_KUMKUMA_MATRIMONY_APP"));
        mvc.perform(request().header("Authorization", jwt("SERVICE"))).andExpect(status().isOk());
        verify(templates, org.mockito.Mockito.times(2)).findByActiveTrue();
    }

    @Test
    void theInternalFilterGrantsOnlyTheServiceAuthorityAndIgnoresOtherPaths() throws Exception {
        NmsInternalServiceAuthFilter filter = filter(SERVICE_TOKEN);
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/nms/logs");
        other.setServletPath("/api/v1/nms/logs");
        MockFilterChain passThrough = new MockFilterChain();
        filter.doFilter(other, new MockHttpServletResponse(), passThrough);
        assertThat(passThrough.getRequest()).isNotNull();

        MockHttpServletRequest internal = new MockHttpServletRequest("GET", PATH);
        internal.setServletPath(PATH);
        internal.addHeader("X-Rider-Service-Token", SERVICE_TOKEN);
        List<String> seen = new java.util.ArrayList<>();
        filter.doFilter(internal, new MockHttpServletResponse(), (req, res) -> SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities().forEach(a -> seen.add(a.getAuthority())));
        assertThat(seen).containsExactly("SERVICE_CREDENTIAL");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(NmsInternalServiceAuthFilter.isInternalPath(null)).isFalse();
    }

    @Test
    void theUserFilterLeavesInternalPathsToTheInternalFilter() throws Exception {
        NmsProperties props = new NmsProperties();
        props.getSecurity().setEnabled(true);
        props.getSecurity().setInternalApiKey(INTERNAL_KEY);
        NmsAuthenticationFilter userFilter = new NmsAuthenticationFilter(props,
                new ObjectMapper().registerModule(new JavaTimeModule()), jwtSupport);
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", PATH);
        internal.setServletPath(PATH);
        internal.addHeader("X-NMS-API-Key", INTERNAL_KEY);
        MockFilterChain chain = new MockFilterChain();

        userFilter.doFilter(internal, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).as("the API key opened nothing").isNull();
    }
}
