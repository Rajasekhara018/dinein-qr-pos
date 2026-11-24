package com.railcab.dms.readiness;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import com.railcab.dms.exception.GlobalExceptionHandler;
import com.railcab.dms.readiness.adapter.in.InternalReadinessController;
import com.railcab.dms.readiness.application.DmsReadinessService;
import com.railcab.dms.security.InternalAuthProperties;
import com.railcab.dms.security.InternalServiceAuthFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of GET /api/v1/dms/internal/readiness: service credential only, 400 on blank params, the report shape. */
class InternalReadinessControllerTest {

    private static final String PATH = "/api/v1/dms/internal/readiness";
    private static final String SECRET = "0123456789-0123456789-0123456789-abc";

    private DocumentTypeQueryService query;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        query = mock(DocumentTypeQueryService.class);
        DmsReadinessService service = new DmsReadinessService(query,
                Clock.fixed(Instant.parse("2026-10-26T10:15:30Z"), ZoneOffset.UTC), Duration.ofSeconds(2));
        mvc = MockMvcBuilders.standaloneSetup(new InternalReadinessController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build()))
                .addFilters(new InternalServiceAuthFilter(new InternalAuthProperties("shared-token", SECRET)))
                .build();
    }

    private static MockHttpServletRequestBuilder request() {
        return get(PATH).servletPath(PATH);
    }

    private static String jwt(String role) {
        return "Bearer " + Jwts.builder().subject("rider-uam").claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void withoutAServiceCredentialItIs401AndNothingIsRead() throws Exception {
        mvc.perform(request().param("applicationCode", "BESTRIDE_RIDER_APP").param("tenantId", "org-a"))
                .andExpect(status().isUnauthorized());
        mvc.perform(request().param("applicationCode", "BESTRIDE_RIDER_APP").param("tenantId", "org-a")
                        .header(DmsCommonConstants.SERVICE_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(request().param("applicationCode", "BESTRIDE_RIDER_APP").param("tenantId", "org-a")
                        .header("Authorization", jwt("ADMIN")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(query);
    }

    @Test
    void blankOrMissingParamsAre400InTheStandardEnvelope() throws Exception {
        mvc.perform(request().param("applicationCode", " ").param("tenantId", "org-a")
                        .header(DmsCommonConstants.SERVICE_TOKEN_HEADER, "shared-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("applicationCode and tenantId are required"));
        mvc.perform(request().param("applicationCode", "BESTRIDE_RIDER_APP")
                        .header(DmsCommonConstants.SERVICE_TOKEN_HEADER, "shared-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(query);
    }

    @Test
    void theServiceGetsTheReportInTheContractShapeScopedToTheGivenOrganization() throws Exception {
        when(query.list(eq("org-a"), isNull(), isNull(), eq(Boolean.TRUE), anyInt(), anyInt()))
                .thenReturn(new DocumentTypeRepository.Page(List.of(), 0, 1, 0, 0));

        mvc.perform(request().param("applicationCode", "BESTRIDE_RIDER_APP").param("tenantId", "org-a")
                        .header("Authorization", jwt("SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.service").value("dms"))
                .andExpect(jsonPath("$.responseObject.applicationCode").value("BESTRIDE_RIDER_APP"))
                .andExpect(jsonPath("$.responseObject.tenantId").value("org-a"))
                .andExpect(jsonPath("$.responseObject.evaluatedAt").value("2026-10-26T10:15:30Z"))
                .andExpect(jsonPath("$.responseObject.checks", hasSize(1)))
                .andExpect(jsonPath("$.responseObject.checks[0].id").value("DMS_DOCUMENT_TYPES_CONFIGURED"))
                .andExpect(jsonPath("$.responseObject.checks[0].status").value("FAIL"))
                .andExpect(jsonPath("$.responseObject.checks[0].messageKey").value("readiness.dms.documentTypesConfigured.fail"))
                .andExpect(jsonPath("$.responseObject.checks[0].params.activeTypes").value("0"))
                .andExpect(jsonPath("$.responseObject.checks[0].params.scope").value("ORGANIZATION"))
                .andExpect(jsonPath("$.responseObject.checks[0].fixPath")
                        .value("/document-management/document-types?application=BESTRIDE_RIDER_APP"));
        verify(query).list(eq("org-a"), isNull(), isNull(), eq(Boolean.TRUE), any(), any());
    }
}
