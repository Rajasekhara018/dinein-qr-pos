package com.railcab.dms.document.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.document.persistence.DocumentMetadataStore;
import com.railcab.dms.documenttype.domain.DocumentTypeRule;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.security.DmsPrincipal;
import com.railcab.dms.storage.StorageProviderRegistry;
import com.railcab.dms.storage.local.LocalStorageProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Storage path (organization / category / type / owner / file), retention from the document type, mandatory scans, and
 * the guarantee that documents stored under the old key shape keep loading, downloading and deleting.
 */
class DocumentUploadStorageTest {

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");
    private static final String SECRET = "unit-test-download-secret-with-32-chars!!";

    @TempDir
    Path root;

    private final DmsProperties properties = new DmsProperties();
    private final DocumentValidationService validationService = mock(DocumentValidationService.class);
    private final DocumentAccessService accessService = mock(DocumentAccessService.class);
    private final DocumentAuditService auditService = mock(DocumentAuditService.class);
    private final DocumentScanService scanService = mock(DocumentScanService.class);
    private final DocumentMetadataStore repository = mock(DocumentMetadataStore.class);
    private final DocumentObjectService objectService = mock(DocumentObjectService.class);
    private DocumentLifecycleService lifecycleService;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        properties.getSecurity().setDownloadTokenSecret(SECRET);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        lifecycleService = new DocumentLifecycleService(properties, clock);
        service = serviceWith(objectService);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectService.store(any(), any())).thenAnswer(invocation -> new DocumentObjectService.StoredDocument(
                invocation.getArgument(0), "/url", "local", null, "sum", "application/pdf", 3L, "NONE"));
        when(scanService.scan(any(), any(), eq(false))).thenReturn(new DocumentScanService.ScanResult("CLEAN", "ok"));
        when(scanService.scan(any(), any(), eq(true))).thenReturn(new DocumentScanService.ScanResult("CLEAN", "ok"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private DocumentService serviceWith(DocumentObjectService objects) {
        return new DocumentService(validationService, objects, accessService, auditService, scanService, lifecycleService,
                new DocumentDownloadTokenService(properties), repository, new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void authenticate(String userId, String tenantId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal(userId, "CUSTOMER", tenantId), null, List.of()));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "Id Card.PDF", "application/pdf", "abc".getBytes(StandardCharsets.UTF_8));
    }

    private static DocumentTypeRule rule(Integer retentionDays, boolean scanRequired) {
        return new DocumentTypeRule("PHOTO_ID", "kyc", 5, List.of("PDF"), List.of(".pdf"), List.of("application/pdf"),
                retentionDays, scanRequired);
    }

    /** Reads and closes the resource (an open handle would block deleting the file on Windows). */
    private static String read(Resource resource) throws IOException {
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String uploadedKey() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objectService).store(key.capture(), any());
        return key.getValue();
    }

    private DocumentMetadata savedMetadata() {
        ArgumentCaptor<DocumentMetadata> metadata = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(repository).save(metadata.capture());
        return metadata.getValue();
    }

    // ------------------------------------------------------------------------------------------------- path

    @Test
    void uploadWithTypeStoresUnderTenantCategoryTypeOwner() {
        authenticate("u1", "org-a");
        when(validationService.validate(any(), eq("kyc"), any(), eq("photo_id"))).thenReturn(Optional.of(rule(null, true)));

        service.upload(pdf(), "kyc", "u1", null, null, "photo_id");

        String key = uploadedKey();
        assertTrue(key.matches("org-a/kyc/PHOTO_ID/u1/[0-9a-f]{8}_id_card\\.pdf"), key);
        assertEquals("org-a", savedMetadata().getTenantId());
        assertEquals(key, savedMetadata().getObjectKey());
    }

    @Test
    void uploadWithoutTypeUsesGeneralAndNoOrganizationFallback() {
        authenticate("u1", null);
        when(validationService.validate(any(), any(), any(), any())).thenReturn(Optional.empty());

        service.upload(pdf(), "kyc", "u1", null, null);

        assertTrue(uploadedKey().matches("no-organization/kyc/general/u1/[0-9a-f]{8}_id_card\\.pdf"), uploadedKey());
    }

    @Test
    void differentOrganizationsNeverShareAPrefix() {
        when(validationService.validate(any(), any(), any(), any())).thenReturn(Optional.empty());
        authenticate("u1", "org-a");
        service.upload(pdf(), "kyc", "u1", null, null);
        String keyA = uploadedKey();

        org.mockito.Mockito.clearInvocations(objectService);
        authenticate("u1", "org-b");
        service.upload(pdf(), "kyc", "u1", null, null);
        String keyB = uploadedKey();

        assertTrue(keyA.startsWith("org-a/"));
        assertTrue(keyB.startsWith("org-b/"));
    }

    @Test
    void hostileOwnerOrTenantIsRejectedAndNothingIsStored() {
        when(validationService.validate(any(), any(), any(), any())).thenReturn(Optional.empty());
        authenticate("u1", "org-a");
        assertThrows(DmsException.class, () -> service.upload(pdf(), "kyc", "../x", null, null));
        assertThrows(DmsException.class, () -> service.upload(pdf(), "..", "u1", null, null));
        authenticate("u1", "..");
        assertThrows(DmsException.class, () -> service.upload(pdf(), "kyc", "u1", null, null));
        verify(objectService, never()).store(any(), any());
        verify(repository, never()).save(any());
    }

    // --------------------------------------------------------------------------------------- retention

    @Test
    void typeRetentionWinsOverTheServiceDefault() {
        authenticate("u1", "org-a");
        properties.getLifecycle().setDefaultRetentionDays(7);
        when(validationService.validate(any(), any(), any(), eq("PHOTO_ID"))).thenReturn(Optional.of(rule(365, true)));

        service.upload(pdf(), "kyc", "u1", null, null, "PHOTO_ID");

        assertEquals(NOW.plusSeconds(365L * 86400), savedMetadata().getRetentionUntil());
        assertEquals(NOW, savedMetadata().getCreatedAt());
    }

    @Test
    void typeWithoutRetentionFallsBackToDefaultOrNone() {
        authenticate("u1", "org-a");
        when(validationService.validate(any(), any(), any(), eq("PHOTO_ID"))).thenReturn(Optional.of(rule(null, true)));

        properties.getLifecycle().setDefaultRetentionDays(7);
        service.upload(pdf(), "kyc", "u1", null, null, "PHOTO_ID");
        assertEquals(NOW.plusSeconds(7L * 86400), savedMetadata().getRetentionUntil());
    }

    @Test
    void noTypeAndNoDefaultMeansNoRetention() {
        authenticate("u1", "org-a");
        when(validationService.validate(any(), any(), any(), any())).thenReturn(Optional.empty());

        service.upload(pdf(), "kyc", "u1", null, null);

        assertNull(savedMetadata().getRetentionUntil());
    }

    // -------------------------------------------------------------------------------------------- scan

    @Test
    void scanRequiredFlagReachesTheScanService() {
        authenticate("u1", "org-a");
        when(validationService.validate(any(), any(), any(), eq("A"))).thenReturn(Optional.of(rule(null, true)));
        when(validationService.validate(any(), any(), any(), eq("B"))).thenReturn(Optional.of(rule(null, false)));

        service.upload(pdf(), "kyc", "u1", null, null, "A");
        verify(scanService).scan(any(), any(), eq(true));

        org.mockito.Mockito.clearInvocations(scanService);
        service.upload(pdf(), "kyc", "u1", null, null, "B");
        verify(scanService).scan(any(), any(), eq(false));
    }

    @Test
    void uploadWithNoTypeIsNeverMandatoryScan() {
        authenticate("u1", "org-a");
        when(validationService.validate(any(), any(), any(), any())).thenReturn(Optional.empty());

        service.upload(pdf(), "kyc", "u1", null, null);

        verify(scanService).scan(any(), any(), eq(false));
    }

    @Test
    void failedRequiredScanStoresNothing() {
        authenticate("u1", "org-a");
        when(validationService.validate(any(), any(), any(), eq("A"))).thenReturn(Optional.of(rule(null, true)));
        when(scanService.scan(any(), any(), eq(true))).thenThrow(DmsException.serviceUnavailable("no scan"));

        DmsException ex = assertThrows(DmsException.class, () -> service.upload(pdf(), "kyc", "u1", null, null, "A"));

        assertEquals(503, ex.getStatus().value());
        verify(objectService, never()).store(any(), any());
        verify(repository, never()).save(any());
    }

    // ------------------------------------------------------------------- documents stored under the old key shape

    @Test
    void documentStoredUnderTheOldKeyShapeStillLoadsDownloadsAndDeletes() throws IOException {
        properties.getStorage().setLocalRoot(root.toString());
        LocalStorageProvider local = new LocalStorageProvider(properties);
        DocumentObjectService realObjects = new DocumentObjectService(properties,
                new StorageProviderRegistry(properties, List.of(local)));
        DocumentService legacyService = serviceWith(realObjects);

        String oldKey = "driver_docs/owner-1/abcd1234_licence.pdf";
        Path file = root.resolve(oldKey);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "legacy-bytes");
        DocumentMetadata legacy = new DocumentMetadata();
        legacy.setId("legacy-1");
        legacy.setObjectKey(oldKey);
        legacy.setProvider("local");
        legacy.setOwnerId("owner-1");
        when(repository.findByObjectKeyAndDeletedFalse(oldKey)).thenReturn(Optional.of(legacy));
        when(repository.findByIdAndDeletedFalse("legacy-1")).thenReturn(Optional.of(legacy));
        authenticate("owner-1", "org-a");

        assertEquals("legacy-bytes", read(legacyService.loadByObjectKey(oldKey)));

        String url = legacyService.createDownloadUrl("legacy-1").url();
        String token = url.substring(url.lastIndexOf('/') + 1);
        assertEquals("legacy-bytes", read(legacyService.loadByDownloadToken(token)));

        legacyService.delete("legacy-1");
        assertFalse(Files.exists(file), "the object is removed by its stored key");
        assertTrue(legacy.isDeleted());
        assertEquals(oldKey, legacy.getObjectKey(), "old keys are never rewritten");
    }

    @Test
    void newNestedKeyRoundTripsThroughTheDownloadToken() throws IOException {
        properties.getStorage().setLocalRoot(root.toString());
        DocumentObjectService realObjects = new DocumentObjectService(properties,
                new StorageProviderRegistry(properties, List.of(new LocalStorageProvider(properties))));
        DocumentService realService = serviceWith(realObjects);
        authenticate("u1", "org-a");
        when(validationService.validate(any(), any(), any(), eq("PHOTO_ID"))).thenReturn(Optional.of(rule(null, true)));

        var response = realService.upload(pdf(), "kyc", "u1", null, null, "PHOTO_ID");
        DocumentMetadata saved = savedMetadata();
        assertTrue(Files.exists(root.resolve(saved.getObjectKey())));
        saved.setId("new-1");
        when(repository.findByIdAndDeletedFalse("new-1")).thenReturn(Optional.of(saved));

        String url = realService.createDownloadUrl("new-1").url();
        Resource resource = realService.loadByDownloadToken(url.substring(url.lastIndexOf('/') + 1));

        assertEquals("abc", read(resource));
        assertEquals("local", response.provider());
    }
}
