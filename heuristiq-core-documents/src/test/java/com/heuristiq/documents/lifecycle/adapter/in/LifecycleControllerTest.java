package com.heuristiq.documents.lifecycle.adapter.in;

import com.heuristiq.documents.common.paging.PagedResult;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentSearchCriteria;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.exception.GlobalExceptionHandler;
import com.heuristiq.documents.lifecycle.application.LifecycleQueryService;
import com.heuristiq.documents.security.DmsPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Request/response shape, organization source and error mapping of the lifecycle endpoints (no security filters). */
class LifecycleControllerTest {

    private static final String BASE = "/api/v1/dms/lifecycle";
    private final LifecycleQueryService service = mock(LifecycleQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LifecycleController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
                .build();
        authenticate("org-a");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String tenant) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal("user-1", "ADMIN", tenant), null, List.of()));
    }

    private static DocumentMetadata document() {
        return DocumentMetadata.builder().id("d1").originalFileName("id.pdf").category("kyc").documentType("PHOTO_ID")
                .ownerId("u1").contentType("application/pdf").sizeBytes(3).status("UPLOADED").scanStatus("CLEAN")
                .legalHold(true).deleted(false).tenantId("org-a").objectKey("org-a/kyc/PHOTO_ID/u1/x.pdf").provider("s3")
                .versionId("v1").checksumSha256("abc").encryption("SSE_S3").scanMessage("ok").uploadedBy("u1")
                .storedFileName("x.pdf").createdAt(Instant.parse("2026-03-01T00:00:00Z"))
                .retentionUntil(Instant.parse("2026-03-31T00:00:00Z")).build();
    }

    @Test
    void searchReturnsThePageShapeWithRowFields() throws Exception {
        when(service.searchDocuments(eq("org-a"), any(), any(), any()))
                .thenReturn(new PagedResult<>(List.of(document()), 2, 10, 31));

        mvc.perform(get(BASE + "/documents?page=2&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.page").value(2))
                .andExpect(jsonPath("$.responseObject.size").value(10))
                .andExpect(jsonPath("$.responseObject.totalElements").value(31))
                .andExpect(jsonPath("$.responseObject.totalPages").value(4))
                .andExpect(jsonPath("$.responseObject.content[0].id").value("d1"))
                .andExpect(jsonPath("$.responseObject.content[0].originalFileName").value("id.pdf"))
                .andExpect(jsonPath("$.responseObject.content[0].documentType").value("PHOTO_ID"))
                .andExpect(jsonPath("$.responseObject.content[0].legalHold").value(true))
                .andExpect(jsonPath("$.responseObject.content[0].deleted").value(false))
                .andExpect(jsonPath("$.responseObject.content[0].retentionUntil").exists())
                .andExpect(jsonPath("$.responseObject.content[0].objectKey").doesNotExist());
    }

    @Test
    void filtersAreParsedAndTheOrganizationComesFromTheSessionNeverTheRequest() throws Exception {
        when(service.searchDocuments(any(), any(), any(), any())).thenReturn(new PagedResult<>(List.of(), 0, 20, 0));

        mvc.perform(get(BASE + "/documents").param("tenantId", "org-b").param("tenant", "org-b").param("query", " inv% ")
                        .param("ownerId", "u1").param("category", "kyc").param("documentType", "PHOTO_ID")
                        .param("status", "UPLOADED").param("scanStatus", "CLEAN").param("deleted", "true")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-02-01T00:00:00Z")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<DocumentSearchCriteria> criteria = ArgumentCaptor.forClass(DocumentSearchCriteria.class);
        verify(service).searchDocuments(eq("org-a"), criteria.capture(), eq(1), eq(5));
        verify(service, never()).searchDocuments(eq("org-b"), any(), any(), any());
        DocumentSearchCriteria c = criteria.getValue();
        assertEquals("inv%", c.query(), "trimmed, and kept literal (escaping happens in the store)");
        assertEquals("u1", c.ownerId());
        assertEquals("kyc", c.category());
        assertEquals("PHOTO_ID", c.documentType());
        assertEquals("UPLOADED", c.status());
        assertEquals("CLEAN", c.scanStatus());
        assertEquals(Boolean.TRUE, c.deleted());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), c.from());
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), c.to());
    }

    @Test
    void blankFiltersAreIgnored() throws Exception {
        when(service.searchDocuments(any(), any(), any(), any())).thenReturn(new PagedResult<>(List.of(), 0, 20, 0));

        mvc.perform(get(BASE + "/documents").param("query", "").param("ownerId", " ").param("from", "")).andExpect(status().isOk());

        ArgumentCaptor<DocumentSearchCriteria> criteria = ArgumentCaptor.forClass(DocumentSearchCriteria.class);
        verify(service).searchDocuments(eq("org-a"), criteria.capture(), any(), any());
        assertEquals(new DocumentSearchCriteria(null, null, null, null, null, null, null, null, null), criteria.getValue());
    }

    @Test
    void badDatesAndBadBooleansAre400() throws Exception {
        mvc.perform(get(BASE + "/documents?from=yesterday")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get(BASE + "/documents?to=2026-13-45")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/documents?deleted=maybe")).andExpect(status().isBadRequest());
        verify(service, never()).searchDocuments(any(), any(), any(), any());
    }

    @Test
    void sessionWithoutOrganizationGets400FromTheService() throws Exception {
        authenticate(null);
        when(service.searchDocuments(any(), any(), any(), any())).thenThrow(DmsException.badRequest("Your session has no organization"));

        mvc.perform(get(BASE + "/documents")).andExpect(status().isBadRequest());
        verify(service).searchDocuments(eq(null), any(), any(), any());
    }

    @Test
    void detailReturnsRowAndDetailFields() throws Exception {
        when(service.getDocument("org-a", "d1")).thenReturn(document());

        mvc.perform(get(BASE + "/documents/d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.id").value("d1"))
                .andExpect(jsonPath("$.responseObject.storedFileName").value("x.pdf"))
                .andExpect(jsonPath("$.responseObject.objectKey").value("org-a/kyc/PHOTO_ID/u1/x.pdf"))
                .andExpect(jsonPath("$.responseObject.provider").value("s3"))
                .andExpect(jsonPath("$.responseObject.versionId").value("v1"))
                .andExpect(jsonPath("$.responseObject.checksumSha256").value("abc"))
                .andExpect(jsonPath("$.responseObject.encryption").value("SSE_S3"))
                .andExpect(jsonPath("$.responseObject.scanMessage").value("ok"))
                .andExpect(jsonPath("$.responseObject.uploadedBy").value("u1"))
                .andExpect(jsonPath("$.responseObject.tenantId").value("org-a"))
                .andExpect(jsonPath("$.responseObject.deletedAt").doesNotExist());
    }

    @Test
    void foreignOrMissingDocumentIs404() throws Exception {
        when(service.getDocument("org-a", "doc-of-b")).thenThrow(DmsException.notFound("Document not found"));

        mvc.perform(get(BASE + "/documents/doc-of-b")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Document not found"));
    }
}
