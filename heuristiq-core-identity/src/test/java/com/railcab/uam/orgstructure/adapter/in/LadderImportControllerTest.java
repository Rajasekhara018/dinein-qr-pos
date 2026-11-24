package com.heuristiq.uam.orgstructure.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.RequestValidationException;
import com.heuristiq.uam.orgstructure.application.LadderImportUseCase;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRow;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRowInput;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderBatch;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.security.TenantContext;

import jakarta.validation.Validation;

/**
 * The ladders import API over the real controller and its error mapping, with the use case stubbed: the status codes, the JSON
 * the web app reads, and the rule that the organization and the actor always come from the session, never from the request.
 */
class LadderImportControllerTest {
    private LadderImportUseCase useCase;
    private LadderImportController controller;
    private MockMvc mvc;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        useCase = mock(LadderImportUseCase.class);
        CommonRequestMapper mapper = new CommonRequestMapper(new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator());
        controller = new LadderImportController(useCase, mapper, new CallerContext("default"));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new OrgStructureExceptionAdvice()).build();
        auth = new UsernamePasswordAuthenticationToken("actor-1", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.set("orgA");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static LadderBatch batch(String status) {
        return new LadderBatch("imp-1", status, "doc-1", "ladders.csv", 10, 8, 2, 0, 0, 1, null, "actor-1",
                Instant.parse("2026-10-26T10:00:00Z"), null);
    }

    private static final String BODY = "{\"requestObject\":{\"documentId\":\"doc-1\"}}";

    @Test
    void startingAnImportAnswers201WithTheReportAndUsesTheSessionOrganizationAndActor() throws Exception {
        when(useCase.upload("orgA", "actor-1", "doc-1")).thenReturn(batch("VALIDATED"));

        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports").principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.id").value("imp-1"))
                .andExpect(jsonPath("$.responseObject.status").value("VALIDATED"))
                .andExpect(jsonPath("$.responseObject.fileName").value("ladders.csv"))
                .andExpect(jsonPath("$.responseObject.totalRows").value(10))
                .andExpect(jsonPath("$.responseObject.validRows").value(8))
                .andExpect(jsonPath("$.responseObject.errorRows").value(2))
                .andExpect(jsonPath("$.responseObject.createdCount").value(0))
                .andExpect(jsonPath("$.responseObject.laddersCreated").value(1))
                .andExpect(jsonPath("$.responseObject.updatedCount").value(0));
    }

    @Test
    void theOrganizationAlwaysComesFromTheSessionAndABodyThatNamesOneIsRefused() throws Exception {
        TenantContext.set("orgB");
        when(useCase.upload(anyString(), anyString(), anyString())).thenReturn(batch("VALIDATED"));

        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports").principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(useCase).upload("orgB", "actor-1", "doc-1");

        // the request type has no organization field, and a body that tries to add one is not accepted at all
        assertThatThrownBy(() -> mvc.perform(post("/api/v1/uam/org-structure/ladder-imports").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"requestObject\":{\"documentId\":\"doc-1\",\"tenantId\":\"evil\"}}")))
                .hasMessageContaining("Unknown field: tenantId");
        verify(useCase, never()).upload(eq("evil"), anyString(), anyString());
    }

    @Test
    void aMissingOrOversizedDocumentIdIsRejectedBeforeAnythingIsRead() {
        CommonRequestObject missing = new CommonRequestObject();
        missing.setRequestObject(Map.of("documentId", ""));
        CommonRequestObject tooLong = new CommonRequestObject();
        tooLong.setRequestObject(Map.of("documentId", "d".repeat(65)));

        assertThatThrownBy(() -> controller.create(auth, missing)).isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> controller.create(auth, tooLong)).isInstanceOf(RequestValidationException.class);
        verify(useCase, never()).upload(any(), any(), any());
    }

    @Test
    void theListIsCappedByTheUseCaseAndDefaultsToFifty() throws Exception {
        when(useCase.recent("orgA", 50)).thenReturn(List.of(batch("APPLIED")));
        when(useCase.recent("orgA", 5)).thenReturn(List.of());

        mvc.perform(get("/api/v1/uam/org-structure/ladder-imports")).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject[0].id").value("imp-1"));
        mvc.perform(get("/api/v1/uam/org-structure/ladder-imports").param("limit", "5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").isEmpty());
    }

    @Test
    void readingOneImportAnswersItsStatusAndCounts() throws Exception {
        when(useCase.batch("orgA", "imp-1")).thenReturn(batch("APPLYING"));

        mvc.perform(get("/api/v1/uam/org-structure/ladder-imports/imp-1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.status").value("APPLYING"));
    }

    @Test
    void aMissingImportIs404WithTheStandardEnvelope() throws Exception {
        when(useCase.batch("orgA", "nope")).thenThrow(OrgStructureException.notFound("Import not found: nope"));

        mvc.perform(get("/api/v1/uam/org-structure/ladder-imports/nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Import not found: nope"))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void theRejectedRowsCarryTheFieldsTheWebAppShowsAndTheKeysetParameters() throws Exception {
        LadderRow bad = new LadderRow(new LadderRowInput(7, "Tech", "Engineer", "200", "yes"), "INVALID",
                List.of("RANK_INVALID", "DUPLICATE_ROW:3"));
        when(useCase.errors("orgA", "imp-1", 3, 20)).thenReturn(List.of(bad));

        mvc.perform(get("/api/v1/uam/org-structure/ladder-imports/imp-1/errors").param("afterRow", "3").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject[0].rowNo").value(7))
                .andExpect(jsonPath("$.responseObject[0].ladder").value("Tech"))
                .andExpect(jsonPath("$.responseObject[0].position").value("Engineer"))
                .andExpect(jsonPath("$.responseObject[0].rank").value("200"))
                .andExpect(jsonPath("$.responseObject[0].managerial").value("yes"))
                .andExpect(jsonPath("$.responseObject[0].errors[0]").value("RANK_INVALID"))
                .andExpect(jsonPath("$.responseObject[0].errors[1]").value("DUPLICATE_ROW:3"));
    }

    @Test
    void confirmingStartsTheApplyAsTheSignedInUserAndAnswersAtOnce() throws Exception {
        when(useCase.confirm("orgA", "actor-1", "imp-1")).thenReturn(batch("APPLYING"));

        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports/imp-1/confirm").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.status").value("APPLYING"));
    }

    @Test
    void confirmingAnImportThatIsNotConfirmableIs409() throws Exception {
        when(useCase.confirm("orgA", "actor-1", "imp-1")).thenThrow(OrgStructureException.conflict("Import is APPLYING and cannot be confirmed"));

        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports/imp-1/confirm").principal(auth)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void confirmingWithNothingValidIs400() throws Exception {
        when(useCase.confirm("orgA", "actor-1", "imp-1")).thenThrow(OrgStructureException.invalid("There are no valid rows to import"));

        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports/imp-1/confirm").principal(auth)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("There are no valid rows to import"));
    }

    @Test
    void cancellingAnswersOkAndIsScopedToTheSessionOrganization() throws Exception {
        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports/imp-1/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Import cancelled"));

        verify(useCase).cancel("orgA", "imp-1");
    }

    @Test
    void aDocumentStoreOutageIs503NotAnAnswerAboutTheFile() throws Exception {
        when(useCase.upload("orgA", "actor-1", "doc-1"))
                .thenThrow(new DownstreamUnavailableException("dms-service", DownstreamUnavailableException.Reason.CIRCUIT_OPEN, null));

        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports").principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("DOWNSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("A required service is temporarily unavailable; try again shortly"));
    }

    @Test
    void aMissingDocumentIs404AndAFileThatIsNotACsvIs400() throws Exception {
        when(useCase.upload("orgA", "actor-1", "doc-1")).thenThrow(OrgStructureException.notFound("The uploaded file was not found: doc-1"));
        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports").principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());

        doThrow(OrgStructureException.invalid("Missing column(s): ladder, position, rank")).when(useCase).upload("orgA", "actor-1", "doc-1");
        mvc.perform(post("/api/v1/uam/org-structure/ladder-imports").principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Missing column(s): ladder, position, rank"));
    }
}
