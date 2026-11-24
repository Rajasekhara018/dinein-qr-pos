package com.railcab.dms.documenttype.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeDraft;
import com.railcab.dms.documenttype.domain.DocumentTypeFields;
import com.railcab.dms.documenttype.domain.DocumentTypeValidator;
import com.railcab.dms.documenttype.domain.VerificationMethod;
import com.railcab.dms.documenttype.support.FakeChangeRequestGateway;
import com.railcab.dms.documenttype.support.InMemoryDocumentTypeRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.MAPPER;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.ORG_A;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.applyPayload;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.caller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verification in the change-request snapshots, their canonical JSON and hash, and through submit and apply. */
class DocumentTypeVerificationSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private static DocumentType type(boolean verificationRequired, VerificationMethod method) {
        return new DocumentType("id-1", ORG_A, "DRIVER_PAN", "driver-documents", "DRIVER_ONBOARDING", Map.of("en", "PAN"),
                EnumSet.of(DocumentFormat.PDF), 5, false, null, 2920, true, verificationRequired, method, true, 4L, NOW, NOW,
                "system", "system", null, List.of());
    }

    private static DocumentTypeFields fields(Boolean verificationRequired, String method) {
        return new DocumentTypeFields(null, "driver-documents", "DRIVER_ONBOARDING", Map.of("en", "PAN"), List.of("PDF"), 5,
                false, null, 2920, true, verificationRequired, method, true);
    }

    @Test
    void aRequiredVerificationAndItsMethodAreInTheCanonicalJson() {
        String canonical = CanonicalJson.canonical(MAPPER.valueToTree(
                DocumentTypeSnapshots.stateOf(type(true, VerificationMethod.PAN_VERIFICATION))));

        assertTrue(canonical.contains("\"verificationMethod\":\"PAN_VERIFICATION\""), canonical);
        assertTrue(canonical.contains("\"verificationRequired\":true"), canonical);
        // Keys are sorted, so the two fields sit between tenantId and version.
        assertTrue(canonical.indexOf("\"tenantId\"") < canonical.indexOf("\"verificationMethod\""));
        assertTrue(canonical.indexOf("\"verificationRequired\"") < canonical.indexOf("\"version\""));
    }

    @Test
    void aTypeWithoutVerificationHashesExactlyAsBeforeTheFieldsExisted() {
        DocumentType withoutVerification = type(false, VerificationMethod.NONE);
        DocumentType legacy = new DocumentType("id-1", ORG_A, "DRIVER_PAN", "driver-documents", "DRIVER_ONBOARDING",
                Map.of("en", "PAN"), EnumSet.of(DocumentFormat.PDF), 5, false, null, 2920, true, true, 4L, NOW, NOW,
                "system", "system", null, List.of());
        Map<String, Object> state = DocumentTypeSnapshots.stateOf(withoutVerification);

        assertFalse(state.containsKey(DocumentTypeConstants.K_VERIFICATION_REQUIRED));
        assertFalse(state.containsKey(DocumentTypeConstants.K_VERIFICATION_METHOD));
        assertEquals(DocumentTypeSnapshots.hashOf(MAPPER, legacy), DocumentTypeSnapshots.hashOf(MAPPER, withoutVerification));
    }

    @Test
    void changingOnlyVerificationChangesTheHashAndTheSnapshot() {
        String none = DocumentTypeSnapshots.hashOf(MAPPER, type(false, VerificationMethod.NONE));
        String pan = DocumentTypeSnapshots.hashOf(MAPPER, type(true, VerificationMethod.PAN_VERIFICATION));
        String manual = DocumentTypeSnapshots.hashOf(MAPPER, type(true, VerificationMethod.MANUAL_REVIEW));

        assertNotEquals(none, pan);
        assertNotEquals(pan, manual);
    }

    @Test
    void theProposedSnapshotCarriesVerificationAndReadsBackIntoTheSameFields() {
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(fields(true, "bank_penny_drop"), false);
        Map<String, Object> after = DocumentTypeSnapshots.proposedOf("id-1", ORG_A, "DRIVER_BANK_PROOF", draft, "maker-1",
                DocumentTypeConstants.ACTION_UPDATE, 4L);

        assertEquals(true, after.get(DocumentTypeConstants.K_VERIFICATION_REQUIRED));
        assertEquals("BANK_PENNY_DROP", after.get(DocumentTypeConstants.K_VERIFICATION_METHOD));
        JsonNode json = MAPPER.valueToTree(after);
        DocumentTypeFields back = DocumentTypeSnapshots.fieldsOf(json);
        assertEquals(Boolean.TRUE, back.verificationRequired());
        assertEquals("BANK_PENNY_DROP", back.verificationMethod());
    }

    @Test
    void aSnapshotWithoutTheFieldsReadsBackAsNoVerification() {
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(fields(null, null), false);
        JsonNode json = MAPPER.valueToTree(DocumentTypeSnapshots.proposedOf(null, ORG_A, "DRIVER_PAN", draft, "m",
                DocumentTypeConstants.ACTION_CREATE, null));

        DocumentTypeFields back = DocumentTypeSnapshots.fieldsOf(json);

        assertNull(back.verificationRequired());
        assertNull(back.verificationMethod());
        DocumentTypeDraft again = DocumentTypeValidator.normalize(back, false);
        assertFalse(again.verificationRequired());
        assertEquals(VerificationMethod.NONE, again.verificationMethod());
    }

    @Test
    void anUpdateThatOnlyTurnsVerificationOnIsSubmittedAsARealChangeAndApplied() {
        InMemoryDocumentTypeRepository repository = new InMemoryDocumentTypeRepository();
        FakeChangeRequestGateway gateway = new FakeChangeRequestGateway();
        DocumentTypeSubmitService submit = new DocumentTypeSubmitService(repository, gateway);
        DocumentTypeApplyService apply = new DocumentTypeApplyService(repository, MAPPER, Clock.fixed(NOW, ZoneOffset.UTC));
        DocumentType stored = repository.save(new DocumentType("id-1", ORG_A, "DRIVER_PAN", "driver-documents",
                "DRIVER_ONBOARDING", Map.of("en", "PAN"), EnumSet.of(DocumentFormat.PDF), 5, false, null, 2920, true, true,
                null, NOW, NOW, "system", "system", null, List.of()));

        submit.submitUpdate(caller(ORG_A, "maker-1"), stored.id(), fields(true, "PAN_VERIFICATION"), stored.version());
        var submission = gateway.submissions.get(0);
        assertFalse(submission.before().containsKey(DocumentTypeConstants.K_VERIFICATION_METHOD));
        assertEquals("PAN_VERIFICATION", submission.after().get(DocumentTypeConstants.K_VERIFICATION_METHOD));

        apply.apply(applyPayload(submission, "cr-v1", "checker-1", DocumentTypeConstants.SUBJECT_TYPE));

        DocumentType applied = repository.findByCode(ORG_A, "DRIVER_PAN").orElseThrow();
        assertTrue(applied.verificationRequired());
        assertEquals(VerificationMethod.PAN_VERIFICATION, applied.verificationMethod());
        assertEquals(stored.version() + 1, applied.version());
        assertEquals("maker-1", applied.updatedBy());
    }
}
