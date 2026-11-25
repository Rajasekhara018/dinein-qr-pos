package com.heuristiq.documents.documenttype.application;

import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeConstants;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeFields;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRule;
import com.heuristiq.documents.documenttype.support.FakeChangeRequestGateway;
import com.heuristiq.documents.documenttype.support.InMemoryDocumentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.heuristiq.documents.documenttype.support.DocumentTypeFixtures.MAPPER;
import static com.heuristiq.documents.documenttype.support.DocumentTypeFixtures.ORG_A;
import static com.heuristiq.documents.documenttype.support.DocumentTypeFixtures.ORG_B;
import static com.heuristiq.documents.documenttype.support.DocumentTypeFixtures.applyPayload;
import static com.heuristiq.documents.documenttype.support.DocumentTypeFixtures.caller;
import static com.heuristiq.documents.documenttype.support.DocumentTypeFixtures.fields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Maker-checker lifecycle and organization isolation of document types, against in-memory fakes of the ports. */
class DocumentTypeMakerCheckerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private InMemoryDocumentTypeRepository repository;
    private FakeChangeRequestGateway gateway;
    private DocumentTypeSubmitService submit;
    private DocumentTypeApplyService apply;
    private DocumentTypeQueryService query;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDocumentTypeRepository();
        gateway = new FakeChangeRequestGateway();
        submit = new DocumentTypeSubmitService(repository, gateway);
        apply = new DocumentTypeApplyService(repository, MAPPER, Clock.fixed(NOW, ZoneOffset.UTC));
        query = new DocumentTypeQueryService(repository, new BlockedFileTypes() {
            @Override
            public java.util.Collection<String> extensions() {
                return List.of(".exe", ".jpg");
            }

            @Override
            public java.util.Collection<String> contentTypes() {
                return List.of("application/x-msdownload");
            }
        });
    }

    /** Submits a create as {@code maker} in {@code tenant}, has {@code checker} approve it, returns the stored type. */
    private DocumentType createApproved(String tenant, String code) {
        submit.submitCreate(caller(tenant, "maker-1"), fields(code));
        var submission = gateway.submissions.get(gateway.submissions.size() - 1);
        apply.apply(applyPayload(submission, "cr-" + tenant + code, "checker-1", DocumentTypeConstants.SUBJECT_TYPE));
        return repository.findByCode(tenant, code).orElseThrow();
    }

    @Test
    void submitStoresNothingAndSendsSnapshotsWithTokenAndSubject() {
        SubmissionResult result = submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID"));

        assertEquals("PENDING", result.status());
        assertEquals(0, repository.size(), "nothing is stored before approval");
        var sent = gateway.submissions.get(0);
        assertEquals("DMS_DOCUMENT_TYPE", sent.subjectType());
        assertEquals("org-a|PHOTO_ID", sent.subjectId());
        assertEquals("Document type PHOTO_ID", sent.title());
        assertTrue(sent.before().isEmpty());
        assertEquals("maker-1", sent.after().get("maker"));
        assertEquals("org-a", sent.after().get("tenantId"));
        assertEquals("CREATE", sent.after().get("action"));
        assertEquals("token-of-maker-1", gateway.tokens.get(0));
    }

    @Test
    void workflowDownGives503KindAndNothingIsStored() {
        gateway.down = true;
        DocumentTypeException ex = assertThrows(DocumentTypeException.class,
                () -> submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID")));
        assertEquals(DocumentTypeException.Kind.APPROVAL_UNAVAILABLE, ex.kind());
        assertEquals(0, repository.size());
    }

    @Test
    void invalidFieldsAndMissingOrganizationAreRejectedBeforeSubmitting() {
        assertThrows(DocumentTypeException.class, () -> submit.submitCreate(caller(ORG_A, "m"),
                new DocumentTypeFields("x", "docs", null, Map.of(), List.of(), 0, null, null, null)));
        assertThrows(DocumentTypeException.class, () -> submit.submitCreate(caller(null, "m"), fields("PHOTO_ID")));
        assertTrue(gateway.submissions.isEmpty());
    }

    @Test
    void approvedCreateIsStoredWithMakerAndChangeRequest() {
        DocumentType stored = createApproved(ORG_A, "PHOTO_ID");

        assertEquals(0L, stored.version());
        assertEquals("maker-1", stored.createdBy());
        assertEquals("maker-1", stored.updatedBy());
        assertEquals("cr-org-aPHOTO_ID", stored.lastChangeRequestId());
        assertEquals(NOW, stored.createdAt());
        assertEquals("photo id", stored.names().get("en").toLowerCase());
        assertEquals(10, stored.expiryYears());
    }

    @Test
    void duplicateCodeIsRejectedOnSubmitAndOnApply() {
        createApproved(ORG_A, "PHOTO_ID");
        assertEquals(DocumentTypeException.Kind.CONFLICT, assertThrows(DocumentTypeException.class,
                () -> submit.submitCreate(caller(ORG_A, "maker-2"), fields("PHOTO_ID"))).kind());

        // A create submitted earlier for the same (then free) code arrives after another one was approved: stale.
        FakeChangeRequestGateway other = new FakeChangeRequestGateway();
        new DocumentTypeSubmitService(new InMemoryDocumentTypeRepository(), other)
                .submitCreate(caller(ORG_A, "maker-2"), fields("PHOTO_ID"));
        String payload = applyPayload(other.submissions.get(0), "cr-late", "checker-1", DocumentTypeConstants.SUBJECT_TYPE);
        assertEquals(DocumentTypeException.Kind.CONFLICT,
                assertThrows(DocumentTypeException.class, () -> apply.apply(payload)).kind());
    }

    @Test
    void makerCannotApproveOwnChange() {
        submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID"));
        String payload = applyPayload(gateway.submissions.get(0), "cr-1", "maker-1", DocumentTypeConstants.SUBJECT_TYPE);
        assertEquals(DocumentTypeException.Kind.FORBIDDEN,
                assertThrows(DocumentTypeException.class, () -> apply.apply(payload)).kind());
        assertEquals(0, repository.size());
    }

    @Test
    void unknownSubjectTypeIsRejected() {
        submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID"));
        String payload = applyPayload(gateway.submissions.get(0), "cr-1", "checker-1", "CONFIG_SETTING");
        assertEquals(DocumentTypeException.Kind.INVALID,
                assertThrows(DocumentTypeException.class, () -> apply.apply(payload)).kind());
        assertThrows(DocumentTypeException.class, () -> apply.apply("not json"));
    }

    @Test
    void repeatedChangeRequestIsIdempotent() {
        submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID"));
        String payload = applyPayload(gateway.submissions.get(0), "cr-1", "checker-1", DocumentTypeConstants.SUBJECT_TYPE);

        var first = apply.apply(payload);
        var second = apply.apply(payload);

        assertEquals("APPLIED", second.status());
        assertEquals(first.id(), second.id());
        assertEquals(1, repository.size());
        assertEquals(0L, repository.findByCode(ORG_A, "PHOTO_ID").orElseThrow().version(), "a repeat changes nothing");
    }

    @Test
    void repeatStaysIdempotentEvenAfterALaterChange() {
        DocumentType created = createApproved(ORG_A, "PHOTO_ID");
        String firstPayload = applyPayload(gateway.submissions.get(0), "cr-org-aPHOTO_ID", "checker-1",
                DocumentTypeConstants.SUBJECT_TYPE);
        submit.submitUpdate(caller(ORG_A, "maker-2"), created.id(), fields("PHOTO_ID"), created.version());
        apply.apply(applyPayload(gateway.submissions.get(1), "cr-2", "checker-1", DocumentTypeConstants.SUBJECT_TYPE));

        assertEquals("APPLIED", apply.apply(firstPayload).status());
        assertEquals(1L, repository.findByCode(ORG_A, "PHOTO_ID").orElseThrow().version());
    }

    @Test
    void updateChangesFieldsKeepsCodeAndBumpsVersion() {
        DocumentType created = createApproved(ORG_A, "PHOTO_ID");
        DocumentTypeFields changed = new DocumentTypeFields("IGNORED_CODE", "matrimony-documents", null, Map.of("en", "Photo ID card"),
                List.of("PNG"), 8, false, null, false);

        submit.submitUpdate(caller(ORG_A, "maker-2"), created.id(), changed, created.version());
        var sent = gateway.submissions.get(1);
        assertEquals("org-a|PHOTO_ID", sent.subjectId());
        assertEquals("UPDATE", sent.after().get("action"));
        apply.apply(applyPayload(sent, "cr-upd", "checker-1", DocumentTypeConstants.SUBJECT_TYPE));

        DocumentType updated = repository.findByCode(ORG_A, "PHOTO_ID").orElseThrow();
        assertEquals("PHOTO_ID", updated.code());
        assertEquals(created.id(), updated.id());
        assertEquals(1L, updated.version());
        assertEquals(8, updated.maxFileSizeMb());
        assertFalse(updated.active());
        assertEquals("maker-1", updated.createdBy());
        assertEquals("maker-2", updated.updatedBy());
        assertEquals("cr-upd", updated.lastChangeRequestId());
        assertEquals(2, updated.recentChangeRequestIds().size());
    }

    @Test
    void staleVersionOnSubmitAndStaleHashOnApplyAreConflicts() {
        DocumentType created = createApproved(ORG_A, "PHOTO_ID");
        assertEquals(DocumentTypeException.Kind.CONFLICT, assertThrows(DocumentTypeException.class,
                () -> submit.submitUpdate(caller(ORG_A, "m"), created.id(), fields("PHOTO_ID"), 7L)).kind());

        // Two updates are submitted against the same version; the first is approved, the second is now stale.
        submit.submitUpdate(caller(ORG_A, "maker-2"), created.id(), fields("PHOTO_ID"), created.version());
        submit.submitUpdate(caller(ORG_A, "maker-3"), created.id(), fields("PHOTO_ID"), created.version());
        apply.apply(applyPayload(gateway.submissions.get(1), "cr-a", "checker-1", DocumentTypeConstants.SUBJECT_TYPE));
        String stale = applyPayload(gateway.submissions.get(2), "cr-b", "checker-1", DocumentTypeConstants.SUBJECT_TYPE);
        assertEquals(DocumentTypeException.Kind.CONFLICT, assertThrows(DocumentTypeException.class, () -> apply.apply(stale)).kind());
        assertEquals(1L, repository.findByCode(ORG_A, "PHOTO_ID").orElseThrow().version());
    }

    // ------------------------------------------------------------------------------------ organization isolation

    @Test
    void sameCodeIsAllowedInTwoOrganizationsAndEachSeesOnlyItsOwn() {
        DocumentType a = createApproved(ORG_A, "PHOTO_ID");
        DocumentType b = createApproved(ORG_B, "PHOTO_ID");
        createApproved(ORG_B, "ONLY_B");

        assertNotNull(a.id());
        assertFalse(a.id().equals(b.id()));
        assertEquals(1, query.list(ORG_A, null, null, null, 0, 20).totalElements());
        assertEquals(2, query.list(ORG_B, null, null, null, 0, 20).totalElements());
        assertEquals(List.of("ONLY_B", "PHOTO_ID"),
                query.list(ORG_B, null, null, null, 0, 20).content().stream().map(DocumentType::code).toList());
    }

    @Test
    void anotherOrganizationsRecordIsNotFoundAndCannotBeChanged() {
        DocumentType a = createApproved(ORG_A, "PHOTO_ID");

        assertEquals(DocumentTypeException.Kind.NOT_FOUND,
                assertThrows(DocumentTypeException.class, () -> query.get(ORG_B, a.id())).kind());
        assertEquals(DocumentTypeException.Kind.NOT_FOUND, assertThrows(DocumentTypeException.class,
                () -> submit.submitUpdate(caller(ORG_B, "m"), a.id(), fields("PHOTO_ID"), a.version())).kind());
        assertEquals(a.id(), query.get(ORG_A, a.id()).id());
    }

    @Test
    void applyOnlyTouchesTheOrganizationInTheApprovedSnapshot() {
        createApproved(ORG_A, "PHOTO_ID");
        // Organization B creates the same code: it must not be seen as a duplicate of A's nor modify A's record.
        DocumentType b = createApproved(ORG_B, "PHOTO_ID");
        assertEquals("org-b", b.tenantId());
        assertEquals(0L, repository.findByCode(ORG_A, "PHOTO_ID").orElseThrow().version());
    }

    @Test
    void resolveRuleIsPerOrganizationAndRejectsUnknownInactiveOrWrongCategory() {
        createApproved(ORG_A, "PHOTO_ID");

        DocumentTypeRule rule = query.resolveRule(ORG_A, "matrimony-documents", "photo_id");
        assertEquals(5 * 1024 * 1024L, rule.maxFileSizeBytes());
        assertEquals(List.of("PDF", "JPG"), rule.allowedFormats());
        assertTrue(rule.allowedExtensions().contains(".pdf"));
        assertFalse(rule.allowedExtensions().contains(".jpg"), "a blocked extension is never allowed");
        assertFalse(rule.allowedContentTypes().isEmpty());

        assertEquals(DocumentTypeException.Kind.INVALID, assertThrows(DocumentTypeException.class,
                () -> query.resolveRule(ORG_B, null, "PHOTO_ID")).kind());
        assertThrows(DocumentTypeException.class, () -> query.resolveRule(ORG_A, "other-category", "PHOTO_ID"));
        assertThrows(DocumentTypeException.class, () -> query.resolveRule(ORG_A, null, "NOPE"));
        assertThrows(DocumentTypeException.class, () -> query.resolveRule(null, null, "PHOTO_ID"));

        DocumentType a = repository.findByCode(ORG_A, "PHOTO_ID").orElseThrow();
        submit.submitUpdate(caller(ORG_A, "m2"), a.id(), new DocumentTypeFields(null, "matrimony-documents", null,
                Map.of("en", "x"), List.of("PDF"), 5, true, null, false), a.version());
        apply.apply(applyPayload(gateway.submissions.get(gateway.submissions.size() - 1), "cr-off", "c",
                DocumentTypeConstants.SUBJECT_TYPE));
        assertThrows(DocumentTypeException.class, () -> query.resolveRule(ORG_A, null, "PHOTO_ID"), "inactive");
    }

    @Test
    void listIsFilteredAndPageSizeIsCapped() {
        createApproved(ORG_A, "TYPE_ONE");
        createApproved(ORG_A, "TYPE_TWO");
        DocumentTypeRepository.Page page = query.list(ORG_A, "MATRIMONY-DOCUMENTS", "matrimony_onboarding", true, -3, 5000);
        assertEquals(100, page.size());
        assertEquals(0, page.page());
        assertEquals(2, page.totalElements());
        assertEquals(0, query.list(ORG_A, "nothing", null, null, 0, 10).totalElements());
        assertThrows(DocumentTypeException.class, () -> query.list(" ", null, null, null, 0, 10));
    }
}
