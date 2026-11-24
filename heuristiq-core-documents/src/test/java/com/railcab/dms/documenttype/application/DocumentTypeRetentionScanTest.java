package com.railcab.dms.documenttype.application;

import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeDraft;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeFields;
import com.railcab.dms.documenttype.domain.DocumentTypeRule;
import com.railcab.dms.documenttype.domain.DocumentTypeValidator;
import com.railcab.dms.documenttype.support.FakeChangeRequestGateway;
import com.railcab.dms.documenttype.support.InMemoryDocumentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-type retention and mandatory-scan fields: validation, maker-checker snapshots, stale detection, rule lookup. */
class DocumentTypeRetentionScanTest {

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
                return List.of(".exe");
            }

            @Override
            public java.util.Collection<String> contentTypes() {
                return List.of();
            }
        });
    }

    private static DocumentTypeFields fields(String code, Integer retentionDays, Boolean scanRequired) {
        return new DocumentTypeFields(code, "matrimony-documents", null, Map.of("en", "Photo ID"), List.of("PDF"), 5, true,
                null, retentionDays, scanRequired, true);
    }

    private void approveLast(String changeRequestId) {
        apply.apply(applyPayload(gateway.submissions.get(gateway.submissions.size() - 1), changeRequestId, "checker-1",
                DocumentTypeConstants.SUBJECT_TYPE));
    }

    private DocumentType stored(String code) {
        return repository.findByCode(ORG_A, code).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ validation

    @Test
    void retentionRangeIsOneToThirtySixThousandFiveHundredOrEmpty() {
        for (int ok : new int[]{1, 365, 36500}) {
            assertEquals(ok, DocumentTypeValidator.normalize(fields("PHOTO_ID", ok, true), true).retentionDays());
        }
        assertNull(DocumentTypeValidator.normalize(fields("PHOTO_ID", null, true), true).retentionDays());
        for (int bad : new int[]{0, -1, 36501, Integer.MAX_VALUE}) {
            DocumentTypeException ex = assertThrows(DocumentTypeException.class,
                    () -> DocumentTypeValidator.normalize(fields("PHOTO_ID", bad, true), true));
            assertEquals(DocumentTypeException.Kind.INVALID, ex.kind());
            assertTrue(ex.getMessage().contains("retentionDays"), ex.getMessage());
        }
    }

    @Test
    void scanRequiredDefaultsToTrue() {
        DocumentTypeDraft missing = DocumentTypeValidator.normalize(fields("PHOTO_ID", null, null), true);
        assertTrue(missing.scanRequired());
        assertFalse(DocumentTypeValidator.normalize(fields("PHOTO_ID", null, false), true).scanRequired());
        DocumentTypeFields legacy = new DocumentTypeFields("PHOTO_ID", "docs", null, Map.of("en", "x"), List.of("PDF"), 5, true, null, true);
        assertTrue(DocumentTypeValidator.normalize(legacy, true).scanRequired());
        assertNull(DocumentTypeValidator.normalize(legacy, true).retentionDays());
    }

    // ------------------------------------------------------------------------------------------- snapshots + hash

    private static DocumentType type(Integer retentionDays, boolean scanRequired) {
        return new DocumentType("id-1", ORG_A, "PHOTO_ID", "docs", null, Map.of("en", "Photo"), EnumSet.of(DocumentFormat.PDF),
                5, true, null, retentionDays, scanRequired, true, 1L, NOW, NOW, "m", "m", "cr", List.of("cr"));
    }

    @Test
    void snapshotOmitsDefaultsSoLegacyHashesAreUnchanged() {
        Map<String, Object> state = DocumentTypeSnapshots.stateOf(type(null, true));
        assertFalse(state.containsKey("retentionDays"));
        assertFalse(state.containsKey("scanRequired"));
    }

    @Test
    void snapshotCarriesNonDefaultValues() {
        Map<String, Object> state = DocumentTypeSnapshots.stateOf(type(90, false));
        assertEquals(90, state.get("retentionDays"));
        assertEquals(false, state.get("scanRequired"));
    }

    @Test
    void changingOnlyRetentionOrOnlyScanChangesTheHash() {
        String base = DocumentTypeSnapshots.hashOf(MAPPER, type(null, true));
        String withRetention = DocumentTypeSnapshots.hashOf(MAPPER, type(90, true));
        String withoutScan = DocumentTypeSnapshots.hashOf(MAPPER, type(null, false));
        String other = DocumentTypeSnapshots.hashOf(MAPPER, type(91, true));

        assertNotEquals(base, withRetention);
        assertNotEquals(base, withoutScan);
        assertNotEquals(withRetention, other);
        assertNotEquals(withRetention, withoutScan);
    }

    @Test
    void proposedSnapshotCarriesTheFieldsAndRoundTripsThroughFieldsOf() {
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(fields("PHOTO_ID", 30, false), true);
        Map<String, Object> after = DocumentTypeSnapshots.proposedOf(null, ORG_A, "PHOTO_ID", draft, "m", "CREATE", null);
        assertEquals(30, after.get("retentionDays"));
        assertEquals(false, after.get("scanRequired"));

        DocumentTypeFields back = DocumentTypeSnapshots.fieldsOf(MAPPER.valueToTree(after));
        assertEquals(30, back.retentionDays());
        assertEquals(false, back.scanRequired());

        Map<String, Object> defaults = DocumentTypeSnapshots.proposedOf(null, ORG_A, "PHOTO_ID",
                DocumentTypeValidator.normalize(fields("PHOTO_ID", null, true), true), "m", "CREATE", null);
        DocumentTypeFields legacyBack = DocumentTypeSnapshots.fieldsOf(MAPPER.valueToTree(defaults));
        assertNull(legacyBack.retentionDays());
        assertNull(legacyBack.scanRequired(), "an old snapshot without the field reads as unset, which validates to true");
    }

    // ------------------------------------------------------------------------------ maker-checker end to end

    @Test
    void createStoresRetentionAndScanSettings() {
        submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID", 365, false));
        assertEquals(365, gateway.submissions.get(0).after().get("retentionDays"));
        approveLast("cr-1");

        assertEquals(365, stored("PHOTO_ID").retentionDays());
        assertFalse(stored("PHOTO_ID").scanRequired());
    }

    @Test
    void anUpdateThatOnlyChangesRetentionIsAChangeThatAppliesAndGoesStaleIfOverlapped() {
        submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID", 30, true));
        approveLast("cr-1");
        DocumentType created = stored("PHOTO_ID");

        submit.submitUpdate(caller(ORG_A, "maker-2"), created.id(), fields("PHOTO_ID", 60, true), created.version());
        submit.submitUpdate(caller(ORG_A, "maker-3"), created.id(), fields("PHOTO_ID", 30, false), created.version());
        var first = gateway.submissions.get(1);
        var second = gateway.submissions.get(2);
        assertNotEquals(first.before(), first.after());
        assertEquals(30, first.before().get("retentionDays"));
        assertEquals(60, first.after().get("retentionDays"));

        apply.apply(applyPayload(first, "cr-2", "checker-1", DocumentTypeConstants.SUBJECT_TYPE));
        assertEquals(60, stored("PHOTO_ID").retentionDays());
        assertTrue(stored("PHOTO_ID").scanRequired());

        String stale = applyPayload(second, "cr-3", "checker-1", DocumentTypeConstants.SUBJECT_TYPE);
        assertEquals(DocumentTypeException.Kind.CONFLICT, assertThrows(DocumentTypeException.class, () -> apply.apply(stale)).kind());
        assertTrue(stored("PHOTO_ID").scanRequired(), "the stale change did not apply");
    }

    @Test
    void anUpdateThatOnlyChangesScanRequiredIsAppliedAndHashed() {
        submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID", null, true));
        approveLast("cr-1");
        DocumentType created = stored("PHOTO_ID");

        submit.submitUpdate(caller(ORG_A, "maker-2"), created.id(), fields("PHOTO_ID", null, false), created.version());
        var sent = gateway.submissions.get(1);
        assertNotEquals(sent.before(), sent.after());
        approveLast("cr-2");

        assertFalse(stored("PHOTO_ID").scanRequired());
        assertNull(stored("PHOTO_ID").retentionDays());
    }

    // --------------------------------------------------------------------------------------------- rule + summary

    @Test
    void resolvedRuleCarriesRetentionAndScan() {
        submit.submitCreate(caller(ORG_A, "maker-1"), fields("PHOTO_ID", 90, false));
        approveLast("cr-1");

        DocumentTypeRule rule = query.resolveRule(ORG_A, "matrimony-documents", "photo_id");

        assertEquals(90, rule.retentionDays());
        assertFalse(rule.scanRequired());
    }

    @Test
    void summaryMentionsRetentionOnlyWhenSet() {
        assertEquals("Accepts PDF, JPG up to 5 MB",
                DocumentTypeSnapshots.summaryOf(EnumSet.of(DocumentFormat.PDF, DocumentFormat.JPG), 5, null));
        assertEquals("Accepts PDF up to 5 MB, retained for 365 days",
                DocumentTypeSnapshots.summaryOf(EnumSet.of(DocumentFormat.PDF), 5, 365));
        assertEquals("Accepts PDF up to 5 MB", DocumentTypeSnapshots.summaryOf(EnumSet.of(DocumentFormat.PDF), 5));
    }
}
