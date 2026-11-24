package com.railcab.dms.documenttype.application;

import com.github.benmanes.caffeine.cache.Ticker;
import com.railcab.dms.common.cache.DmsCacheProperties;
import com.railcab.dms.documenttype.adapter.out.cache.CachingDocumentTypeRules;
import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import com.railcab.dms.documenttype.domain.DocumentTypeRule;
import com.railcab.dms.documenttype.domain.PrebuiltDocumentTypes;
import com.railcab.dms.documenttype.domain.VerificationMethod;
import com.railcab.dms.documenttype.support.InMemoryDocumentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prebuilt catalog is created per organization right after DEFAULT, once, idempotently by code: an existing code is
 * never touched, a concurrent create is read as done, and one organization's types are never another's.
 */
class PrebuiltDocumentTypeSeedingTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ORG_A = "org-a";
    private static final String ORG_B = "org-b";
    private static final int CATALOG_SIZE = PrebuiltDocumentTypes.entries().size();
    private static final DocumentTypeRepository.Query ALL = new DocumentTypeRepository.Query(null, null, null, 0, 100);

    private InMemoryDocumentTypeRepository repository;
    private DefaultDocumentTypeService seeder;
    private DocumentTypeQueryService query;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDocumentTypeRepository();
        seeder = new DefaultDocumentTypeService(repository, CLOCK, settings(10));
        query = new DocumentTypeQueryService(repository, noBlocked(), seeder);
    }

    private static DefaultDocumentTypeService.Settings settings(int maxMb) {
        return new DefaultDocumentTypeService.Settings(true, maxMb, null, true);
    }

    private static BlockedFileTypes noBlocked() {
        return new BlockedFileTypes() {
            @Override
            public Collection<String> extensions() {
                return List.of();
            }

            @Override
            public Collection<String> contentTypes() {
                return List.of();
            }
        };
    }

    private Set<String> codesOf(String tenant) {
        return repository.list(tenant, ALL).content().stream().map(DocumentType::code).collect(Collectors.toSet());
    }

    private static Set<String> expectedCodes() {
        Set<String> codes = PrebuiltDocumentTypes.entries().stream().map(PrebuiltDocumentTypes.Entry::code)
                .collect(Collectors.toCollection(HashSet::new));
        codes.add(DocumentTypeConstants.DEFAULT_TYPE_CODE);
        return codes;
    }

    @Test
    void ensureForCreatesDefaultAndTheWholeCatalogAsTheSystem() {
        DocumentType defaultType = seeder.ensureFor(ORG_A).orElseThrow();

        assertEquals(DocumentTypeConstants.DEFAULT_TYPE_CODE, defaultType.code());
        assertEquals(expectedCodes(), codesOf(ORG_A));
        for (DocumentType type : repository.list(ORG_A, ALL).content()) {
            assertEquals(ORG_A, type.tenantId());
            assertEquals(DocumentTypeConstants.SYSTEM_ACTOR, type.createdBy());
            assertTrue(type.active());
            assertTrue(type.scanRequired());
            assertEquals(0L, type.version());
        }
        DocumentType aadhaar = repository.findByCode(ORG_A, "DRIVER_AADHAAR").orElseThrow();
        assertTrue(aadhaar.verificationRequired());
        assertEquals(VerificationMethod.AADHAAR_DIGILOCKER, aadhaar.verificationMethod());
        assertEquals("driver-documents", aadhaar.category());
    }

    @Test
    void sizesFollowTheConfiguredMaximumOfTheDefaultType() {
        new DefaultDocumentTypeService(repository, CLOCK, settings(4)).ensureFor(ORG_A);
        new DefaultDocumentTypeService(repository, CLOCK, settings(50)).ensureFor(ORG_B);

        assertEquals(4, repository.findByCode(ORG_A, "FRANCHISE_AGREEMENT").orElseThrow().maxFileSizeMb());
        assertEquals(4, repository.findByCode(ORG_A, "DRIVER_PAN").orElseThrow().maxFileSizeMb());
        assertEquals(20, repository.findByCode(ORG_B, "FRANCHISE_AGREEMENT").orElseThrow().maxFileSizeMb());
        assertEquals(5, repository.findByCode(ORG_B, "DRIVER_PAN").orElseThrow().maxFileSizeMb());
    }

    @Test
    void runningAgainCreatesNothingAndChangesNothing() {
        seeder.ensureFor(ORG_A);
        Map<String, Long> versions = repository.list(ORG_A, ALL).content().stream()
                .collect(Collectors.toMap(DocumentType::code, DocumentType::version));

        seeder.ensureFor(ORG_A);
        new DefaultDocumentTypeService(repository, CLOCK, settings(10)).ensureFor(ORG_A);

        assertEquals(CATALOG_SIZE + 1, repository.size());
        repository.list(ORG_A, ALL).content().forEach(t -> assertEquals(versions.get(t.code()), t.version(), t.code()));
    }

    @Test
    void anExistingCodeIsNeverOverwrittenEvenWhenItDiffersFromTheCatalog() {
        DocumentType own = repository.save(new DocumentType("own-id", ORG_A, "DRIVER_PAN", "my-category", null,
                Map.of("en", "Our PAN"), EnumSet.of(DocumentFormat.PDF), 2, false, null, null, false, false,
                VerificationMethod.NONE, false, null, NOW, NOW, "maker", "maker", "cr-1", List.of("cr-1")));

        seeder.ensureFor(ORG_A);

        DocumentType after = repository.findByCode(ORG_A, "DRIVER_PAN").orElseThrow();
        assertEquals(own, after);
        assertEquals(CATALOG_SIZE + 1, repository.size());
    }

    @Test
    void anOrganizationThatAlreadyHadItsDefaultStillGetsTheCatalog() {
        // An organization seeded before the catalog existed: DEFAULT only.
        new DefaultDocumentTypeService(repository, CLOCK, new DefaultDocumentTypeService.Settings(true, 10, null)).ensureFor(ORG_A);
        DocumentType before = repository.findByCode(ORG_A, DocumentTypeConstants.DEFAULT_TYPE_CODE).orElseThrow();
        assertEquals(1, repository.size());

        DocumentType defaultType = seeder.ensureFor(ORG_A).orElseThrow();

        assertEquals(before, defaultType);
        assertEquals(expectedCodes(), codesOf(ORG_A));
    }

    @Test
    void aConcurrentCreateOfOneCatalogCodeIsReadAsDoneAndTheRestIsStillCreated() {
        AtomicInteger conflicts = new AtomicInteger();
        InMemoryDocumentTypeRepository racing = new InMemoryDocumentTypeRepository() {
            @Override
            public DocumentType save(DocumentType type) {
                if ("VEHICLE_RC".equals(type.code())) {
                    conflicts.incrementAndGet();
                    throw DocumentTypeException.conflict("A document type with code VEHICLE_RC already exists");
                }
                return super.save(type);
            }
        };

        new DefaultDocumentTypeService(racing, CLOCK, settings(10)).ensureFor(ORG_A);

        assertEquals(1, conflicts.get());
        assertEquals(CATALOG_SIZE, racing.size());
        assertTrue(racing.findByCode(ORG_A, "VEHICLE_RC").isEmpty());
        assertTrue(racing.findByCode(ORG_A, "VEHICLE_INSURANCE").isPresent());
    }

    @Test
    void anyOtherStoreFailureWhileSeedingTheCatalogIsNotSwallowed() {
        InMemoryDocumentTypeRepository broken = new InMemoryDocumentTypeRepository() {
            @Override
            public DocumentType save(DocumentType type) {
                if ("CUSTOMER_PHOTO".equals(type.code())) {
                    throw DocumentTypeException.invalid("store is down");
                }
                return super.save(type);
            }
        };
        DefaultDocumentTypeService service = new DefaultDocumentTypeService(broken, CLOCK, settings(10));

        assertThrows(DocumentTypeException.class, () -> service.ensureFor(ORG_A));
        // Not remembered as done, so the next call tries again (and fails again while the store is down).
        assertTrue(broken.findByCode(ORG_A, DocumentTypeConstants.DEFAULT_TYPE_CODE).isPresent());
        assertThrows(DocumentTypeException.class, () -> service.ensureOnce(ORG_A));
    }

    @Test
    void withTheCatalogSwitchedOffOnlyTheDefaultIsCreated() {
        new DefaultDocumentTypeService(repository, CLOCK, new DefaultDocumentTypeService.Settings(true, 10, null, false))
                .ensureFor(ORG_A);
        assertEquals(Set.of(DocumentTypeConstants.DEFAULT_TYPE_CODE), codesOf(ORG_A));

        new DefaultDocumentTypeService(repository, CLOCK, new DefaultDocumentTypeService.Settings(false, 10, null, true))
                .ensureFor(ORG_B);
        assertTrue(codesOf(ORG_B).isEmpty());
    }

    @Test
    void ensureOnceLooksUpTheCatalogOnlyOncePerOrganization() {
        AtomicInteger finds = new AtomicInteger();
        InMemoryDocumentTypeRepository counting = new InMemoryDocumentTypeRepository() {
            @Override
            public Optional<DocumentType> findByCode(String tenantId, String code) {
                finds.incrementAndGet();
                return super.findByCode(tenantId, code);
            }
        };
        DefaultDocumentTypeService service = new DefaultDocumentTypeService(counting, CLOCK, settings(10));

        service.ensureOnce(ORG_A);
        int afterFirst = finds.get();
        service.ensureOnce(ORG_A);

        assertTrue(afterFirst >= CATALOG_SIZE + 1);
        assertEquals(afterFirst, finds.get());
    }

    // ---- widening types an older catalog created --------------------------------------------------------------------

    /** The franchise agreement as the older, PDF-only catalog created it: by the system, never edited. */
    private DocumentType oldPdfOnlyAgreement(String tenant, String updatedBy, String lastChangeRequestId) {
        return repository.save(new DocumentType("agreement-" + tenant, tenant, "FRANCHISE_AGREEMENT", "franchise-onboarding",
                "FRANCHISE_ONBOARDING", Map.of("en", "Franchise agreement"), EnumSet.of(DocumentFormat.PDF), 20, true, null,
                3650, true, false, VerificationMethod.NONE, true, null, NOW, NOW, DocumentTypeConstants.SYSTEM_ACTOR,
                updatedBy, lastChangeRequestId, lastChangeRequestId == null ? List.of() : List.of(lastChangeRequestId)));
    }

    @Test
    void anUntouchedPdfOnlyAgreementIsWidenedSoJpegAndPngScansUpload() {
        DocumentType before = oldPdfOnlyAgreement(ORG_A, DocumentTypeConstants.SYSTEM_ACTOR, null);

        seeder.ensureFor(ORG_A);

        DocumentType after = repository.findByCode(ORG_A, "FRANCHISE_AGREEMENT").orElseThrow();
        assertEquals(EnumSet.of(DocumentFormat.PDF, DocumentFormat.JPG, DocumentFormat.PNG), after.allowedFormats());
        assertEquals(before.id(), after.id());
        assertEquals(before.version() + 1, after.version());
        assertEquals(before.maxFileSizeMb(), after.maxFileSizeMb());
        DocumentTypeRule rule = query.resolveRule(ORG_A, "franchise-onboarding", "FRANCHISE_AGREEMENT");
        assertTrue(rule.allowedContentTypes().containsAll(List.of("application/pdf", "image/jpeg", "image/png")));
        assertTrue(rule.allowedExtensions().containsAll(List.of(".pdf", ".jpg", ".jpeg", ".png")));
        assertTrue(repository.findByCode(ORG_B, "FRANCHISE_AGREEMENT").isEmpty(), "widening A writes nothing for B");
    }

    @Test
    void anAgreementAnAdminChangedIsNeverWidened() {
        DocumentType edited = oldPdfOnlyAgreement(ORG_A, "ops-admin", "cr-7");

        seeder.ensureFor(ORG_A);

        assertEquals(edited, repository.findByCode(ORG_A, "FRANCHISE_AGREEMENT").orElseThrow());
    }

    @Test
    void wideningIsIdempotentAndANarrowerOrDifferentSetIsLeftAlone() {
        oldPdfOnlyAgreement(ORG_A, DocumentTypeConstants.SYSTEM_ACTOR, null);
        seeder.ensureFor(ORG_A);
        Long widenedVersion = repository.findByCode(ORG_A, "FRANCHISE_AGREEMENT").orElseThrow().version();
        new DefaultDocumentTypeService(repository, CLOCK, settings(10)).ensureFor(ORG_A);
        assertEquals(widenedVersion, repository.findByCode(ORG_A, "FRANCHISE_AGREEMENT").orElseThrow().version());

        PrebuiltDocumentTypes.Entry agreement = PrebuiltDocumentTypes.entries().stream()
                .filter(e -> e.code().equals("FRANCHISE_AGREEMENT")).findFirst().orElseThrow();
        DocumentType withDocx = new DocumentType("x", ORG_B, "FRANCHISE_AGREEMENT", "franchise-onboarding", null,
                Map.of("en", "A"), EnumSet.of(DocumentFormat.PDF, DocumentFormat.DOCX), 20, true, null, null, true,
                false, VerificationMethod.NONE, true, 0L, NOW, NOW, DocumentTypeConstants.SYSTEM_ACTOR,
                DocumentTypeConstants.SYSTEM_ACTOR, null, List.of());
        assertTrue(PrebuiltDocumentTypes.widenedToCatalog(agreement, withDocx, NOW).isEmpty());
    }

    // ---- organization isolation ---------------------------------------------------------------------------------------

    @Test
    void organizationAsCatalogIsNotOrganizationBs() {
        seeder.ensureFor(ORG_A);
        assertTrue(codesOf(ORG_B).isEmpty(), "seeding A writes nothing for B");
        DocumentType aPan = repository.findByCode(ORG_A, "DRIVER_PAN").orElseThrow();

        DocumentTypeRepository.Page bPage = query.list(ORG_B, null, null, null, 0, 100);

        assertEquals(CATALOG_SIZE + 1, bPage.totalElements());
        bPage.content().forEach(t -> assertEquals(ORG_B, t.tenantId()));
        DocumentType bPan = repository.findByCode(ORG_B, "DRIVER_PAN").orElseThrow();
        assertFalse(aPan.id().equals(bPan.id()));
        assertEquals(DocumentTypeException.Kind.NOT_FOUND,
                assertThrows(DocumentTypeException.class, () -> query.get(ORG_B, aPan.id())).kind());
        assertEquals(DocumentTypeException.Kind.NOT_FOUND,
                assertThrows(DocumentTypeException.class, () -> query.get(ORG_A, bPan.id())).kind());
    }

    @Test
    void aChangeInOneOrganizationsCatalogTypeDoesNotReachTheOther() {
        seeder.ensureFor(ORG_A);
        seeder.ensureFor(ORG_B);
        DocumentType aPan = repository.findByCode(ORG_A, "DRIVER_PAN").orElseThrow();
        repository.save(new DocumentType(aPan.id(), ORG_A, aPan.code(), aPan.category(), aPan.flow(), aPan.names(),
                aPan.allowedFormats(), aPan.maxFileSizeMb(), aPan.required(), aPan.expiryYears(), aPan.retentionDays(),
                aPan.scanRequired(), false, VerificationMethod.NONE, false, aPan.version(), NOW, NOW, "system", "ops",
                "cr-2", List.of("cr-2")));

        DocumentType bPan = repository.findByCode(ORG_B, "DRIVER_PAN").orElseThrow();
        assertTrue(bPan.active());
        assertEquals(VerificationMethod.PAN_VERIFICATION, bPan.verificationMethod());
    }

    // ---- the rule uploads see, and the category check (contract section 3) -------------------------------------------

    @Test
    void theRuleOfACatalogTypeCarriesItsVerificationThroughTheCache() {
        CachingDocumentTypeRules cached = new CachingDocumentTypeRules(query,
                new DmsCacheProperties(true, new DmsCacheProperties.Spec(true, 60, 100)), Ticker.disabledTicker(), null);

        DocumentTypeRule rule = cached.resolveRule(ORG_A, "driver-documents", "driving_licence");
        DocumentTypeRule again = cached.resolveRule(ORG_A, "driver-documents", "DRIVING_LICENCE");

        assertEquals("DRIVING_LICENCE", rule.documentTypeCode());
        assertTrue(rule.verificationRequired());
        assertEquals(VerificationMethod.DRIVING_LICENCE_SARATHI, rule.verificationMethod());
        assertEquals(List.of("PDF", "JPG", "PNG"), rule.allowedFormats());
        assertEquals(2920, rule.retentionDays());
        assertEquals(rule, again);
        assertFalse(cached.resolveRule(ORG_A, "customer-documents", "CUSTOMER_PHOTO").verificationRequired());
    }

    @Test
    void anUploadWhoseCategoryDiffersFromTheTypesCategoryIsRejectedNotFallenBack() {
        DocumentTypeException ex = assertThrows(DocumentTypeException.class,
                () -> query.resolveRule(ORG_A, "profile_photo", "DRIVER_AADHAAR"));

        assertEquals(DocumentTypeException.Kind.INVALID, ex.kind());
        assertTrue(ex.getMessage().contains("does not belong to category"));
        assertEquals("DRIVER_AADHAAR", query.resolveRule(ORG_A, " Driver-Documents ", "DRIVER_AADHAAR").documentTypeCode());
        assertEquals("DRIVER_AADHAAR", query.resolveRule(ORG_A, null, "DRIVER_AADHAAR").documentTypeCode());
    }
}
