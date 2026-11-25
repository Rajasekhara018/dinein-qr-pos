package com.heuristiq.documents.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.documents.centralaudit.AuditPublishProperties;
import com.heuristiq.documents.centralaudit.CentralAuditPublisher;
import com.heuristiq.documents.centralaudit.InMemoryAuditOutboxStore;
import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.document.audit.DocumentAuditEvent;
import com.heuristiq.documents.document.audit.DocumentAuditStore;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.documenttype.application.DocumentTypeRules;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRule;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.scan.DocumentContentSniffer;
import com.heuristiq.documents.scan.ScanProvider;
import com.heuristiq.documents.scan.ScanProviderRegistry;
import com.heuristiq.documents.security.DmsPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The events a real upload / download pipeline queues for the central audit service: one per action, with reason codes
 * for refusals, tenant isolation of what is queued, and no effect on the operation when the audit side is down.
 */
class DocumentLifecycleEventsTest {

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DmsProperties properties = new DmsProperties();
    private final InMemoryAuditOutboxStore outbox = new InMemoryAuditOutboxStore();
    private final List<DocumentAuditEvent> localEvents = new ArrayList<>();
    private boolean localStoreDown;
    private final DocumentMetadataStore repository = mock(DocumentMetadataStore.class);
    private final DocumentObjectService objectService = mock(DocumentObjectService.class);
    private ScanProvider.ScanVerdict verdict = new ScanProvider.ScanVerdict(true, "ok");
    private DocumentService service;
    private DocumentAuditService auditService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        properties.getScan().setProvider("stub");
        DocumentAuditStore localStore = event -> {
            if (localStoreDown) {
                throw new IllegalStateException("local audit store down");
            }
            localEvents.add(event);
            return event;
        };
        AuditPublishProperties publish = new AuditPublishProperties(true, "", "http://x", "t", 100, 5000, 60, 5, 15, 900, 7,
                3000, 3);
        auditService = new DocumentAuditService(localStore, new CentralAuditPublisher(outbox, MAPPER, publish, clock), clock);
        ScanProvider stub = new ScanProvider() {
            @Override
            public boolean supports(String provider) {
                return "stub".equals(provider);
            }

            @Override
            public ScanVerdict scan(MultipartFile file) {
                return verdict;
            }
        };
        DocumentScanService scan = new DocumentScanService(properties, new DocumentContentSniffer(),
                new ScanProviderRegistry(properties, List.of(stub)));
        DocumentTypeRules rules = (tenant, category, code) -> {
            if ("NOPE".equalsIgnoreCase(code)) {
                throw DocumentTypeException.invalid("Unknown or inactive document type NOPE");
            }
            return new DocumentTypeRule("PHOTO_ID", "kyc", 1, List.of("PDF"), List.of(".pdf"), List.of("application/pdf"),
                    30, !"OPTIONAL".equalsIgnoreCase(code));
        };
        DocumentLifecycleService lifecycle = new DocumentLifecycleService(properties, clock);
        service = new DocumentService(new DocumentValidationService(properties, rules), objectService,
                new DocumentAccessService(properties), auditService, scan, lifecycle,
                new DocumentDownloadTokenService(properties), repository, new SimpleMeterRegistry(), clock);
        properties.getSecurity().setDownloadTokenSecret("unit-test-download-secret-with-32-chars!!");
        when(repository.save(any())).thenAnswer(i -> {
            DocumentMetadata m = i.getArgument(0);
            if (m.getId() == null) {
                m.setId("doc-1");
            }
            return m;
        });
        when(objectService.store(any(), any())).thenAnswer(i -> new DocumentObjectService.StoredDocument(
                i.getArgument(0), "/url", "s3", null, "sum", "application/pdf", 3L, "SSE_S3"));
        authenticate("u1", "CUSTOMER", "org-a");
        MDC.put("correlationId", "corr-42");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static void authenticate(String user, String role, String tenant) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal(user, role, tenant), null, List.of()));
    }

    private static MockMultipartFile pdf(byte[] content) {
        return new MockMultipartFile("file", "id.pdf", "application/pdf", content);
    }

    private List<JsonNode> queued() {
        return outbox.all().stream().map(e -> {
            try {
                return MAPPER.readTree(e.payload());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }).toList();
    }

    private JsonNode only(String action) {
        List<JsonNode> matching = queued().stream().filter(n -> action.equals(n.get("details").get("action").asText())).toList();
        assertEquals(1, matching.size(), "events for " + action + ": " + queued());
        return matching.get(0);
    }

    private void assertRejected(String reasonCode, String outcome, Runnable upload) {
        assertThrows(DmsException.class, upload::run);
        JsonNode rejected = only("UPLOAD_REJECTED");
        assertEquals(reasonCode, rejected.get("reasonCode").asText());
        assertEquals(reasonCode, rejected.get("details").get("reasonCode").asText());
        assertEquals(outcome, rejected.get("outcome").asText());
        assertEquals("org-a", rejected.get("tenantId").asText());
        assertEquals("corr-42", rejected.get("correlationId").asText());
        assertEquals("u1", rejected.get("userId").asText());
        assertEquals(false, rejected.has("subjectId"), "a refused upload has no document");
        assertEquals("DOMAIN", rejected.get("eventType").asText());
    }

    // --------------------------------------------------------------------------------------- successful upload

    @Test
    void uploadQueuesUploadAndScanResultEventsForTheDocument() {
        service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID");

        JsonNode upload = only("UPLOAD");
        assertEquals("SUCCESS", upload.get("outcome").asText());
        assertEquals("DOCUMENT", upload.get("subjectType").asText());
        assertEquals("doc-1", upload.get("subjectId").asText());
        assertEquals("PHOTO_ID", upload.get("details").get("documentType").asText());
        assertEquals("kyc", upload.get("details").get("category").asText());
        assertEquals("s3", upload.get("details").get("storageProvider").asText());
        assertEquals("CLEAN", upload.get("details").get("scanVerdict").asText());
        assertEquals(3, upload.get("details").get("sizeBytes").asInt());
        JsonNode scan = only("SCAN_RESULT");
        assertEquals("SCAN_CLEAN", scan.get("reasonCode").asText());
        assertEquals("doc-1", scan.get("subjectId").asText());
        assertEquals(1, localEvents.stream().filter(e -> e.getAction().name().equals("UPLOAD")).count(),
                "the existing DMS-local audit keeps working");
    }

    @Test
    void downloadUrlAndDownloadAndDeleteQueueEvents() throws Exception {
        service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID");
        DocumentMetadata stored = new DocumentMetadata();
        stored.setId("doc-1");
        stored.setTenantId("org-a");
        stored.setOwnerId("u1");
        stored.setObjectKey("org-a/kyc/PHOTO_ID/u1/x.pdf");
        stored.setProvider("local");
        stored.setCategory("kyc");
        when(repository.findByIdAndDeletedFalse("doc-1")).thenReturn(Optional.of(stored));
        when(repository.findByObjectKeyAndDeletedFalse("org-a/kyc/PHOTO_ID/u1/x.pdf")).thenReturn(Optional.of(stored));
        when(objectService.presignDownload(any(), any())).thenReturn(Optional.empty());
        when(objectService.load(any(), any())).thenReturn(new org.springframework.core.io.ByteArrayResource(new byte[0]));

        service.createDownloadUrl("doc-1");
        service.loadByObjectKey("org-a/kyc/PHOTO_ID/u1/x.pdf");
        service.delete("doc-1");

        assertEquals("doc-1", only("DOWNLOAD_URL_ISSUED").get("subjectId").asText());
        assertEquals("doc-1", only("DOWNLOAD").get("subjectId").asText());
        assertEquals("doc-1", only("DELETE").get("subjectId").asText());
    }

    // --------------------------------------------------------------------------------------- rejected uploads

    @Test
    void tooLargeFileIsRejectedWithFileTooLarge() {
        assertRejected("FILE_TOO_LARGE", "FAILURE",
                () -> service.upload(pdf(new byte[2 * 1024 * 1024]), "kyc", "u1", null, null, "PHOTO_ID"));
        assertEquals(2 * 1024 * 1024, only("UPLOAD_REJECTED").get("details").get("sizeBytes").asInt());
    }

    @Test
    void wrongContentTypeAndExtensionAreRejectedWithTheirCodes() {
        assertRejected("CONTENT_TYPE_NOT_ALLOWED", "FAILURE", () -> service.upload(
                new MockMultipartFile("file", "id.pdf", "image/png", "abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID"));
    }

    @Test
    void unknownDocumentTypeIsRejected() {
        assertRejected("DOCUMENT_TYPE_INVALID", "FAILURE",
                () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "NOPE"));
        assertEquals("NOPE", only("UPLOAD_REJECTED").get("details").get("documentType").asText());
    }

    @Test
    void checksumMismatchIsRejected() {
        assertRejected("CHECKSUM_MISMATCH", "FAILURE",
                () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, "deadbeef", "PHOTO_ID"));
    }

    @Test
    void infectedFileIsRejectedAndAlsoGetsAScanResultEvent() {
        verdict = new ScanProvider.ScanVerdict(false, "Trojan");

        assertRejected("MALWARE_DETECTED", "FAILURE", () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID"));

        JsonNode scan = only("SCAN_RESULT");
        assertEquals("MALWARE_DETECTED", scan.get("reasonCode").asText());
        assertEquals("INFECTED", scan.get("details").get("scanVerdict").asText());
        assertEquals("FAILURE", scan.get("outcome").asText());
    }

    @Test
    void requiredScanThatCannotRunIsRejectedAs503WithScanUnavailable() {
        properties.getScan().setEnabled(false);

        assertRejected("SCAN_UNAVAILABLE", "FAILURE", () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID"));
        assertEquals("NO_VERDICT", only("SCAN_RESULT").get("details").get("scanVerdict").asText());
    }

    @Test
    void inconclusiveVerdictIsRejectedWhenTheScanIsRequired() {
        verdict = new ScanProvider.ScanVerdict(true, "allowed by fail-open", true);

        assertRejected("SCAN_INCONCLUSIVE", "FAILURE", () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID"));
    }

    @Test
    void passwordProtectedFileIsRejectedWhenTheScanIsRequired() {
        assertRejected("PASSWORD_PROTECTED_NOT_SCANNABLE", "FAILURE",
                () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", "secret", null, "PHOTO_ID"));
    }

    @Test
    void hostilePathPartIsRejectedWithPathInvalid() {
        assertRejected("PATH_INVALID", "FAILURE", () -> service.upload(pdf("abc".getBytes()), "kyc", "../x", null, null, "OPTIONAL"));
    }

    @Test
    void uploadingForAnotherUserIsDeniedNotFailed() {
        assertRejected("ACCESS_DENIED", "DENIED", () -> service.upload(pdf("abc".getBytes()), "kyc", "someone-else", null, null, "OPTIONAL"));
    }

    @Test
    void storageFailureIsRejectedWithStorageFailed() {
        when(objectService.store(any(), any())).thenThrow(DmsException.badRequest("Unable to upload document to S3"));

        assertRejected("STORAGE_FAILED", "FAILURE", () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "OPTIONAL"));
    }

    @Test
    void unexpectedFailureIsRecordedAsUploadFailedWithoutLeakingTheMessage() {
        when(objectService.store(any(), any())).thenThrow(new IllegalStateException("jdbc:postgresql://secret-host password=hunter2"));

        assertThrows(IllegalStateException.class, () -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "OPTIONAL"));

        JsonNode rejected = only("UPLOAD_REJECTED");
        assertEquals("UPLOAD_FAILED", rejected.get("reasonCode").asText());
        assertEquals(false, rejected.toString().contains("hunter2"));
    }

    // ------------------------------------------------------------------------------ isolation + resilience

    @Test
    void everyQueuedEventCarriesTheCallersOrganizationNeverAnother() {
        service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID");
        authenticate("u2", "CUSTOMER", "org-b");
        assertThrows(DmsException.class, () -> service.upload(pdf("abc".getBytes()), "kyc", "../x", null, null, "OPTIONAL"));

        List<JsonNode> events = queued();
        assertTrue(events.stream().anyMatch(n -> "org-a".equals(n.get("tenantId").asText())));
        assertTrue(events.stream().anyMatch(n -> "org-b".equals(n.get("tenantId").asText())));
        events.stream().filter(n -> "u2".equals(n.path("userId").asText())).forEach(n -> assertEquals("org-b", n.get("tenantId").asText()));
        events.stream().filter(n -> "u1".equals(n.path("userId").asText())).forEach(n -> assertEquals("org-a", n.get("tenantId").asText()));
    }

    @Test
    void aCallerWithoutAnOrganizationQueuesNothing() {
        authenticate("svc", "INTERNAL_SERVICE", null);

        assertThrows(DmsException.class, () -> service.upload(pdf("abc".getBytes()), "kyc", "../x", null, null, "OPTIONAL"));

        assertTrue(outbox.all().isEmpty());
    }

    @Test
    void aDownedOutboxNeverBreaksUploadOrRejection() {
        outbox.failInserts = true;

        assertDoesNotThrow(() -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID"));
        assertThrows(DmsException.class, () -> service.upload(pdf("abc".getBytes()), "kyc", "../x", null, null, "OPTIONAL"));
    }

    @Test
    void aDownedLocalAuditStoreNeverBreaksUploadAndTheCentralEventIsStillQueued() {
        localStoreDown = true;

        assertDoesNotThrow(() -> service.upload(pdf("abc".getBytes()), "kyc", "u1", null, null, "PHOTO_ID"));

        assertEquals("doc-1", only("UPLOAD").get("subjectId").asText());
    }

    @Test
    void nothingSecretOrPersonalIsPutInTheEvent() {
        service.upload(new MockMultipartFile("file", "Jane Doe passport.pdf", "application/pdf", "abc".getBytes()), "kyc", "u1",
                null, null, "PHOTO_ID");

        String all = queued().toString();
        assertEquals(false, all.contains("Jane"), all);
        assertEquals(false, all.toLowerCase().contains("password"), all);
        assertEquals(false, all.contains("objectKey"), all);
        assertNull(queued().get(0).get("details").get("originalFileName"));
    }
}
