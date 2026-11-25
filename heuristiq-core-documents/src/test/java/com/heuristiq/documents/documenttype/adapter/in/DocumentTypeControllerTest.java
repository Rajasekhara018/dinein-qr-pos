package com.heuristiq.documents.documenttype.adapter.in;

import com.heuristiq.documents.documenttype.application.CallerContext;
import com.heuristiq.documents.documenttype.application.DocumentTypeApplyService;
import com.heuristiq.documents.documenttype.application.DocumentTypeFileCheckService;
import com.heuristiq.documents.documenttype.application.DocumentTypeQueryService;
import com.heuristiq.documents.documenttype.application.DocumentTypeSubmitService;
import com.heuristiq.documents.documenttype.application.SubmissionResult;
import com.heuristiq.documents.documenttype.domain.DocumentFormat;
import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRule;
import com.heuristiq.documents.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Request/response shape and error mapping of the document-type endpoints (security filters are not part of this slice). */
class DocumentTypeControllerTest {

    private static final String BASE = "/api/v1/dms/document-types";
    private static final String BODY = "{\"code\":\"PHOTO_ID\",\"category\":\"docs\",\"names\":{\"en\":\"Photo ID\"},"
            + "\"allowedFormats\":[\"PDF\"],\"maxFileSizeMb\":5,\"required\":true,\"active\":true,\"version\":2}";

    private final DocumentTypeQueryService query = mock(DocumentTypeQueryService.class);
    private final DocumentTypeSubmitService submit = mock(DocumentTypeSubmitService.class);
    private final DocumentTypeFileCheckService fileCheck = mock(DocumentTypeFileCheckService.class);
    private final DocumentTypeApplyService apply = mock(DocumentTypeApplyService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DocumentTypeController(query, submit, fileCheck),
                new InternalDocumentTypeController(apply)).setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.StringHttpMessageConverter(),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new com.heuristiq.documents.security.DmsPrincipal("user-1", "ADMIN", "org-a"), null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static DocumentType sample() {
        return new DocumentType("id-1", "org-a", "PHOTO_ID", "docs", null, Map.of("en", "Photo ID"),
                EnumSet.of(DocumentFormat.PDF, DocumentFormat.JPG), 5, true, null, null, true, true, 2L, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), "m", "m", "cr-1", List.of("cr-1"));
    }

    @Test
    void listUsesTheSessionOrganizationAndReturnsAPageInTheEnvelope() throws Exception {
        when(query.list("org-a", "docs", null, true, 1, 50)).thenReturn(
                new DocumentTypeRepository.Page(List.of(sample()), 1, 50, 51, 2));

        mvc.perform(get(BASE).param("category", "docs").param("active", "true").param("page", "1").param("size", "50")
                        .param("tenantId", "org-evil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.totalElements").value(51))
                .andExpect(jsonPath("$.responseObject.totalPages").value(2))
                .andExpect(jsonPath("$.responseObject.content[0].code").value("PHOTO_ID"))
                .andExpect(jsonPath("$.responseObject.content[0].summary").value("Accepts PDF, JPG up to 5 MB"))
                .andExpect(jsonPath("$.responseObject.content[0].allowedFormats[1]").value("JPG"));
    }

    @Test
    void getMapsNotFoundTo404() throws Exception {
        when(query.get("org-a", "x")).thenThrow(DocumentTypeException.notFound("Document type not found"));
        mvc.perform(get(BASE + "/x")).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));

        when(query.get("org-a", "id-1")).thenReturn(sample());
        mvc.perform(get(BASE + "/id-1")).andExpect(status().isOk()).andExpect(jsonPath("$.responseObject.version").value(2));
    }

    @Test
    void createReturns202WithTheChangeRequestInTheEnvelopeAndPassesTheCallersToken() throws Exception {
        when(submit.submitCreate(any(), any())).thenReturn(new SubmissionResult("cr-7", "PENDING"));

        mvc.perform(post(BASE).header("Authorization", "Bearer abc.def").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.responseObject.changeRequestId").value("cr-7"))
                .andExpect(jsonPath("$.responseObject.status").value("PENDING"));

        ArgumentCaptor<CallerContext> caller = ArgumentCaptor.forClass(CallerContext.class);
        verify(submit).submitCreate(caller.capture(), any());
        assertEquals(new CallerContext("user-1", "org-a", "abc.def"), caller.getValue());
    }

    @Test
    void updatePassesIdAndVersion() throws Exception {
        when(submit.submitUpdate(any(), eq("id-1"), any(), eq(2L))).thenReturn(new SubmissionResult("cr-8", "PENDING"));
        mvc.perform(put(BASE + "/id-1").header("Authorization", "Bearer t").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.responseObject.changeRequestId").value("cr-8"));
    }

    @Test
    void errorsMapTo400403404409And503() throws Exception {
        Map<DocumentTypeException, Integer> cases = new java.util.LinkedHashMap<>();
        cases.put(DocumentTypeException.invalid("bad"), 400);
        cases.put(DocumentTypeException.forbidden("no"), 403);
        cases.put(DocumentTypeException.notFound("gone"), 404);
        cases.put(DocumentTypeException.conflict("stale"), 409);
        cases.put(DocumentTypeException.approvalUnavailable("down"), 503);
        for (var entry : cases.entrySet()) {
            org.mockito.Mockito.reset(submit);
            when(submit.submitCreate(any(), any())).thenThrow(entry.getKey());
            mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().is(entry.getValue()))
                    .andExpect(jsonPath("$.message").value(entry.getKey().getMessage()));
        }
    }

    @Test
    void unreadableBodyIs400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void checkFileReturnsVerdictAndRule() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "id.png", "image/png", new byte[] {1});
        DocumentTypeRule rule = new DocumentTypeRule("PHOTO_ID", "docs", 5, List.of("PDF"), List.of(".pdf"), List.of("application/pdf"), null, true);
        when(fileCheck.check(eq("org-a"), eq(null), eq("PHOTO_ID"), any())).thenReturn(new DocumentTypeFileCheckService.Result(
                false, List.of(new DocumentTypeFileCheckService.Reason("EXTENSION_NOT_ALLOWED", "Document file extension is not allowed")),
                rule));

        mvc.perform(multipart(BASE + "/check-file").file(file).param("documentType", "PHOTO_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.accepted").value(false))
                .andExpect(jsonPath("$.responseObject.reasons[0].code").value("EXTENSION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.responseObject.rule.maxFileSizeMb").value(5))
                .andExpect(jsonPath("$.responseObject.rule.allowedExtensions[0]").value(".pdf"));

        mvc.perform(multipart(BASE + "/check-file").param("documentType", "PHOTO_ID")).andExpect(status().isBadRequest());
    }

    @Test
    void internalApplyAnswersAppliedAndMapsMakerCheckerErrors() throws Exception {
        when(apply.apply("{}")).thenReturn(new DocumentTypeApplyService.ApplyOutcome("APPLIED", "id-1", 0L, "cr-1"));
        mvc.perform(post("/api/v1/dms/internal/document-types/apply").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responseObject.status").value("APPLIED"));

        when(apply.apply("{\"a\":1}")).thenThrow(DocumentTypeException.conflict("stale"));
        mvc.perform(post("/api/v1/dms/internal/document-types/apply").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isConflict());
        when(apply.apply("{\"b\":1}")).thenThrow(DocumentTypeException.forbidden("The maker cannot approve their own change"));
        mvc.perform(post("/api/v1/dms/internal/document-types/apply").contentType(MediaType.APPLICATION_JSON).content("{\"b\":1}"))
                .andExpect(status().isForbidden());
    }
}
