package com.heuristiq.documents.documenttype.application;

import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.documents.common.cache.DmsCacheProperties;
import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.document.application.DocumentValidationService;
import com.heuristiq.documents.documenttype.adapter.out.cache.CachingDocumentTypeRules;
import com.heuristiq.documents.documenttype.domain.DefaultDocumentTypes;
import com.heuristiq.documents.documenttype.domain.DocumentFormat;
import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeConstants;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRule;
import com.heuristiq.documents.documenttype.support.InMemoryDocumentTypeRepository;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.readiness.application.DmsReadinessService;
import com.heuristiq.documents.readiness.domain.ReadinessStatus;
import com.heuristiq.documents.security.DmsPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built-in DEFAULT document type: created on first need without approval, never overwritten, isolated per organization,
 * used as the fallback for an unknown type code, and still subject to the blocked-file rules.
 */
class DefaultDocumentTypeTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ORG_A = "org-a";
    private static final String ORG_B = "org-b";

    private final DmsProperties properties = new DmsProperties();
    private InMemoryDocumentTypeRepository repository;
    private DefaultDocumentTypeService defaults;
    private DocumentTypeQueryService query;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDocumentTypeRepository();
        defaults = new DefaultDocumentTypeService(repository, CLOCK, new DefaultDocumentTypeService.Settings(true, 10, null));
        query = new DocumentTypeQueryService(repository, blocked(), defaults);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private BlockedFileTypes blocked() {
        return new BlockedFileTypes() {
            @Override
            public Collection<String> extensions() {
                return properties.getScan().getBlockedExtensions();
            }

            @Override
            public Collection<String> contentTypes() {
                return properties.getScan().getBlockedContentTypes();
            }
        };
    }

    private void as(String tenant) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal("user-1", "ADMIN", tenant), null, List.of()));
    }

    private DocumentType store(String tenant, String code, String category, boolean active) {
        return repository.save(new DocumentType(UUID.randomUUID().toString(), tenant, code, category, null, Map.of("en", code),
                EnumSet.of(DocumentFormat.PDF), 2, true, null, null, true, active, null, NOW, NOW, "m", "m", "cr", List.of()));
    }

    // ---- seeding ---------------------------------------------------------------------------------------------------

    @Test
    void createsTheDefaultTypeWithEveryFormatAScanAndNoApproval() {
        DocumentType created = defaults.ensureFor(ORG_A).orElseThrow();

        assertEquals("DEFAULT", created.code());
        assertEquals(ORG_A, created.tenantId());
        assertEquals("general", created.category());
        assertNull(created.flow());
        assertEquals(EnumSet.allOf(DocumentFormat.class), created.allowedFormats());
        assertEquals(10, created.maxFileSizeMb());
        assertTrue(created.scanRequired());
        assertTrue(created.active());
        assertFalse(created.required());
        assertEquals("Default document", created.names().get("en"));
        assertEquals(DocumentTypeConstants.LANGUAGES.size(), created.names().size());
        assertEquals("system", created.createdBy());
        assertEquals("system", created.updatedBy());
        assertNull(created.lastChangeRequestId());
        assertEquals(NOW, created.createdAt());
        assertNotNull(created.version());
    }

    @Test
    void thePublishedBuilderUsesTheGivenSizeAndRetention() {
        DocumentType type = DefaultDocumentTypes.create(ORG_A, "id-1", NOW, 25, 365);

        assertEquals(25, type.maxFileSizeMb());
        assertEquals(365, type.retentionDays());
        assertTrue(DefaultDocumentTypes.isDefaultCode(" default "));
        assertFalse(DefaultDocumentTypes.isDefaultCode("PHOTO_ID"));
        assertFalse(DefaultDocumentTypes.isDefaultCode(null));
    }

    @Test
    void seedingTwiceKeepsTheSameRecordAndNeverOverwritesAnEdit() {
        DocumentType first = defaults.ensureFor(ORG_A).orElseThrow();
        // Ops deactivate it through the normal flow (a normal versioned save).
        DocumentType edited = repository.save(new DocumentType(first.id(), ORG_A, first.code(), first.category(), null,
                first.names(), first.allowedFormats(), 3, false, null, null, true, false, first.version(), NOW, NOW,
                first.createdBy(), "ops-user", "cr-1", List.of("cr-1")));

        DocumentType again = defaults.ensureFor(ORG_A).orElseThrow();

        assertEquals(edited.version(), again.version());
        assertFalse(again.active());
        assertEquals(3, again.maxFileSizeMb());
        assertEquals(1, repository.list(ORG_A, new DocumentTypeRepository.Query(null, null, null, 0, 50)).totalElements());
    }

    @Test
    void aRaceWithAnotherInstanceIsReadAsSomeoneElseDidIt() {
        DocumentType other = DefaultDocumentTypes.create(ORG_A, "winner", NOW, 10, null);
        AtomicInteger finds = new AtomicInteger();
        DocumentTypeRepository racing = new InMemoryDocumentTypeRepository() {
            @Override
            public Optional<DocumentType> findByCode(String tenantId, String code) {
                // Empty on the first read (before the other instance wrote), present afterwards.
                return finds.getAndIncrement() == 0 ? Optional.empty() : Optional.of(other);
            }

            @Override
            public DocumentType save(DocumentType type) {
                throw DocumentTypeException.conflict("A document type with code DEFAULT already exists");
            }
        };

        Optional<DocumentType> result = new DefaultDocumentTypeService(racing, CLOCK,
                new DefaultDocumentTypeService.Settings(true, 10, null)).ensureFor(ORG_A);

        assertEquals("winner", result.orElseThrow().id());
    }

    @Test
    void anyOtherStoreFailureIsNotSwallowed() {
        DocumentTypeRepository broken = new InMemoryDocumentTypeRepository() {
            @Override
            public DocumentType save(DocumentType type) {
                throw DocumentTypeException.invalid("store is down");
            }
        };

        DefaultDocumentTypeService service = new DefaultDocumentTypeService(broken, CLOCK,
                new DefaultDocumentTypeService.Settings(true, 10, null));

        assertThrows(DocumentTypeException.class, () -> service.ensureFor(ORG_A));
    }

    @Test
    void aDisabledFeatureAndABlankOrganizationCreateNothing() {
        DefaultDocumentTypeService off = new DefaultDocumentTypeService(repository, CLOCK,
                new DefaultDocumentTypeService.Settings(false, 10, null));

        assertTrue(off.ensureFor(ORG_A).isEmpty());
        off.ensureOnce(ORG_A);
        assertFalse(off.enabled());
        assertTrue(defaults.ensureFor(" ").isEmpty());
        assertTrue(defaults.ensureFor(null).isEmpty());
        defaults.ensureOnce(null);
        assertTrue(DefaultDocumentTypeService.disabled().ensureFor(ORG_A).isEmpty());
        assertEquals(0, repository.list(ORG_A, new DocumentTypeRepository.Query(null, null, null, 0, 50)).totalElements());
    }

    @Test
    void ensureOnceRemembersAnOrganizationAndStopsLookingItUp() {
        AtomicInteger finds = new AtomicInteger();
        DocumentTypeRepository counting = new InMemoryDocumentTypeRepository() {
            @Override
            public Optional<DocumentType> findByCode(String tenantId, String code) {
                finds.incrementAndGet();
                return super.findByCode(tenantId, code);
            }
        };
        DefaultDocumentTypeService service = new DefaultDocumentTypeService(counting, CLOCK,
                new DefaultDocumentTypeService.Settings(true, 10, null));

        service.ensureOnce(ORG_A);
        int afterFirst = finds.get();
        service.ensureOnce(ORG_A);
        service.ensureOnce(ORG_A);

        assertTrue(afterFirst >= 1);
        assertEquals(afterFirst, finds.get());
    }

    // ---- first access, listing, readiness, isolation -----------------------------------------------------------------

    @Test
    void anOrganizationNeverSeesAnEmptyList() {
        DocumentTypeRepository.Page page = query.list(ORG_A, null, null, null, null, null);

        assertEquals(1, page.totalElements());
        assertEquals("DEFAULT", page.content().get(0).code());
    }

    @Test
    void oneOrganizationsDefaultIsInvisibleToAnother() {
        DocumentType a = query.list(ORG_A, null, null, null, null, null).content().get(0);
        DocumentType b = query.list(ORG_B, null, null, null, null, null).content().get(0);

        assertEquals(ORG_A, a.tenantId());
        assertEquals(ORG_B, b.tenantId());
        assertFalse(a.id().equals(b.id()));
        assertThrows(DocumentTypeException.class, () -> query.get(ORG_B, a.id()));
        assertEquals(1, query.list(ORG_B, null, null, null, null, null).totalElements());
    }

    @Test
    void readinessPassesForAnOrganizationThatNeverConfiguredAnything() {
        DmsReadinessService readiness = new DmsReadinessService(query, CLOCK, Duration.ofSeconds(2));

        var check = readiness.evaluate("PASUPU_KUMKUMA_MATRIMONY_APP", "brand-new-org").checks().get(0);

        assertEquals(ReadinessStatus.PASS, check.status());
        assertEquals("1", check.params().get("activeTypes"));
    }

    @Test
    void readinessStillFailsWhenTheDefaultWasDeactivatedAndNothingElseIsActive() {
        DocumentType seeded = defaults.ensureFor(ORG_A).orElseThrow();
        repository.save(new DocumentType(seeded.id(), ORG_A, seeded.code(), seeded.category(), null, seeded.names(),
                seeded.allowedFormats(), 10, false, null, null, true, false, seeded.version(), NOW, NOW, "system", "ops",
                "cr-9", List.of("cr-9")));
        DmsReadinessService readiness = new DmsReadinessService(query, CLOCK, Duration.ofSeconds(2));

        var check = readiness.evaluate("PASUPU_KUMKUMA_MATRIMONY_APP", ORG_A).checks().get(0);

        assertEquals(ReadinessStatus.FAIL, check.status());
    }

    // ---- resolving the rule: order of precedence ---------------------------------------------------------------------

    @Test
    void anUnknownTypeCodeFallsBackToTheDefaultRule() {
        DocumentTypeRule rule = query.resolveRule(ORG_A, "profile_photo", "SELFIE");

        assertEquals("DEFAULT", rule.documentTypeCode());
        assertEquals(10L * 1024 * 1024, rule.maxFileSizeBytes());
        assertTrue(rule.allowedExtensions().containsAll(List.of(".pdf", ".jpg", ".png", ".docx", ".csv")));
        assertTrue(rule.scanRequired());
    }

    @Test
    void aTypeThatExistsKeepsUsingItsOwnRule() {
        store(ORG_A, "PHOTO_ID", "matrimony-documents", true);

        DocumentTypeRule rule = query.resolveRule(ORG_A, "matrimony-documents", "photo_id");

        assertEquals("PHOTO_ID", rule.documentTypeCode());
        assertEquals(2L * 1024 * 1024, rule.maxFileSizeBytes());
        assertEquals(List.of(".pdf"), rule.allowedExtensions());
    }

    @Test
    void aTypeOfAnotherCategoryStaysRejectedAndDoesNotFallBack() {
        store(ORG_A, "PHOTO_ID", "matrimony-documents", true);

        DocumentTypeException ex = assertThrows(DocumentTypeException.class,
                () -> query.resolveRule(ORG_A, "profile_photo", "PHOTO_ID"));

        assertTrue(ex.getMessage().contains("does not belong to category"));
    }

    @Test
    void aTypeSwitchedOffOnPurposeIsRejectedNotReplacedByTheMorePermissiveDefault() {
        store(ORG_A, "OLD_ID", "matrimony-documents", false);

        DocumentTypeException ex = assertThrows(DocumentTypeException.class,
                () -> query.resolveRule(ORG_A, "matrimony-documents", "OLD_ID"));

        assertTrue(ex.getMessage().contains("Unknown or inactive"));
    }

    @Test
    void whenTheDefaultIsDeactivatedAnUnknownCodeIsRejectedAgain() {
        DocumentType seeded = defaults.ensureFor(ORG_A).orElseThrow();
        repository.save(new DocumentType(seeded.id(), ORG_A, seeded.code(), seeded.category(), null, seeded.names(),
                seeded.allowedFormats(), 10, false, null, null, true, false, seeded.version(), NOW, NOW, "system", "ops",
                "cr-9", List.of("cr-9")));

        assertThrows(DocumentTypeException.class, () -> query.resolveRule(ORG_A, "profile_photo", "SELFIE"));
        assertThrows(DocumentTypeException.class, () -> query.resolveRule(ORG_A, "profile_photo", "DEFAULT"));
    }

    @Test
    void whenTheFeatureIsOffAnUnknownCodeIsRejectedAsBefore() {
        DocumentTypeQueryService off = new DocumentTypeQueryService(repository, blocked());

        assertThrows(DocumentTypeException.class, () -> off.resolveRule(ORG_A, "profile_photo", "SELFIE"));
    }

    @Test
    void theDefaultTypeFitsAnyCategoryWhenNamedExplicitly() {
        assertEquals("DEFAULT", query.resolveRule(ORG_A, "aadhaar_card", "default").documentTypeCode());
        assertEquals("DEFAULT", query.resolveRule(ORG_A, null, "DEFAULT").documentTypeCode());
    }

    @Test
    void anotherOrganizationsDefaultIsNeverUsedAsAFallback() {
        store(ORG_B, "PHOTO_ID", "matrimony-documents", true);
        // ORG_A has no PHOTO_ID: it gets its own DEFAULT, never ORG_B's PHOTO_ID.
        DocumentTypeRule rule = query.resolveRule(ORG_A, "matrimony-documents", "PHOTO_ID");

        assertEquals("DEFAULT", rule.documentTypeCode());
        assertEquals(ORG_A, repository.findByCode(ORG_A, "DEFAULT").orElseThrow().tenantId());
    }

    @Test
    void requiresAnOrganizationAndACode() {
        assertThrows(DocumentTypeException.class, () -> query.resolveRule(" ", "x", "DEFAULT"));
        assertThrows(DocumentTypeException.class, () -> query.resolveRule(ORG_A, "x", " "));
    }

    // ---- blocked files stay blocked under the default ------------------------------------------------------------------

    @Test
    void aBlockedExecutableIsStillRejectedWhenTheDefaultTypeAppliesAndAGoodFileIsAccepted() {
        as(ORG_A);
        DocumentValidationService validation = new DocumentValidationService(properties, query);

        assertDoesNotThrow(() -> validation.validate(new MockMultipartFile("file", "notes.pdf", "application/pdf",
                new byte[10]), "profile_photo", null, "SELFIE"));
        assertThrows(DmsException.class, () -> validation.validate(new MockMultipartFile("file", "run.exe",
                "application/x-msdownload", new byte[10]), "profile_photo", null, "SELFIE"));
        assertThrows(DmsException.class, () -> validation.validate(new MockMultipartFile("file", "run.exe",
                "application/pdf", new byte[10]), "profile_photo", null, "SELFIE"));
    }

    @Test
    void theDefaultRuleNeverContainsABlockedExtensionOrContentType() {
        DocumentTypeQueryService blockingPdf = new DocumentTypeQueryService(repository, new BlockedFileTypes() {
            @Override
            public Collection<String> extensions() {
                return List.of(".pdf");
            }

            @Override
            public Collection<String> contentTypes() {
                return List.of("application/pdf");
            }
        }, defaults);

        DocumentTypeRule rule = blockingPdf.resolveRule(ORG_A, "x", "SELFIE");

        assertFalse(rule.allowedExtensions().contains(".pdf"));
        assertFalse(rule.allowedContentTypes().contains("application/pdf"));
        assertTrue(rule.allowedExtensions().contains(".jpg"));
    }

    // ---- the rule cache must not keep a fallback ---------------------------------------------------------------------

    @Test
    void aFallbackAnswerIsNeverCachedSoATypeCreatedLaterIsSeenAtOnce() {
        CachingDocumentTypeRules cached = new CachingDocumentTypeRules(query,
                new DmsCacheProperties(true, new DmsCacheProperties.Spec(true, 60, 100)), Ticker.disabledTicker(), null);

        assertEquals("DEFAULT", cached.resolveRule(ORG_A, "profile_photo", "SELFIE").documentTypeCode());
        store(ORG_A, "SELFIE", "profile_photo", true);

        assertEquals("SELFIE", cached.resolveRule(ORG_A, "profile_photo", "SELFIE").documentTypeCode());
        // The type's own rule is cached and its category is still enforced on a cached hit.
        assertEquals("SELFIE", cached.resolveRule(ORG_A, "profile_photo", "SELFIE").documentTypeCode());
        assertThrows(DocumentTypeException.class, () -> cached.resolveRule(ORG_A, "other", "SELFIE"));
        // The default fits every category, cached or not.
        assertEquals("DEFAULT", cached.resolveRule(ORG_A, "anything", "DEFAULT").documentTypeCode());
        assertEquals("DEFAULT", cached.resolveRule(ORG_A, "something-else", "DEFAULT").documentTypeCode());
    }
}
