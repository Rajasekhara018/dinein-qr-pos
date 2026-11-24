package com.heuristiq.documents.documenttype.domain;

import com.heuristiq.documents.config.DmsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTypeValidatorTest {

    private static DocumentTypeFields valid() {
        return new DocumentTypeFields(" photo_id ", " Docs ", "onboarding", Map.of("en", " Photo ID "),
                List.of("pdf", "JPG"), 5, true, 3, null);
    }

    @Test
    void normalizesCodeCategoryFlowAndNames() {
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(valid(), true);
        assertEquals("PHOTO_ID", draft.code());
        assertEquals("docs", draft.category());
        assertEquals("ONBOARDING", draft.flow());
        assertEquals("Photo ID", draft.names().get("en"));
        assertEquals(2, draft.allowedFormats().size());
        assertTrue(draft.active(), "active defaults to true");
    }

    @Test
    void rejectsBadFields() {
        for (DocumentTypeFields bad : List.of(
                new DocumentTypeFields("ab", "docs", null, Map.of("en", "x"), List.of("PDF"), 5, true, null, true),
                new DocumentTypeFields("OK_CODE", "", null, Map.of("en", "x"), List.of("PDF"), 5, true, null, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("hi", "x"), List.of("PDF"), 5, true, null, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("en", "x", "fr", "y"), List.of("PDF"), 5, true, null, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("en", "x"), List.of(), 5, true, null, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("en", "x"), List.of("EXE"), 5, true, null, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("en", "x"), List.of("PDF"), 0, true, null, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("en", "x"), List.of("PDF"), 51, true, null, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("en", "x"), List.of("PDF"), 5, true, 0, true),
                new DocumentTypeFields("OK_CODE", "docs", null, Map.of("en", "x"), List.of("PDF"), 5, true, 51, true))) {
            DocumentTypeException ex = assertThrows(DocumentTypeException.class, () -> DocumentTypeValidator.normalize(bad, true));
            assertEquals(DocumentTypeException.Kind.INVALID, ex.kind());
        }
    }

    @Test
    void codeIsIgnoredOnUpdate() {
        DocumentTypeFields noCode = new DocumentTypeFields(null, "docs", null, Map.of("en", "x"), List.of("PDF"), 5, true, null, true);
        assertEquals("", DocumentTypeValidator.normalize(noCode, false).code());
    }

    @Test
    void noFormatIsExecutableAndBlockedTypesStayBlocked() {
        DmsProperties.Scan scan = new DmsProperties().getScan();
        for (DocumentFormat format : DocumentFormat.values()) {
            assertFalse(format.extensions().stream().anyMatch(e -> scan.getBlockedExtensions().contains(e)), format.name());
            assertFalse(format.contentTypes().stream().anyMatch(c -> scan.getBlockedContentTypes().contains(c)), format.name());
        }
    }

    @Test
    void formatsCoverExactlyTheConfiguredAllowList() {
        DmsProperties.ValidationRule defaults = new DmsProperties.ValidationRule();
        for (DocumentFormat format : List.of(DocumentFormat.PDF, DocumentFormat.PNG, DocumentFormat.WEBP, DocumentFormat.DOC,
                DocumentFormat.DOCX)) {
            assertTrue(defaults.getAllowedExtensions().containsAll(format.extensions()), format.name());
            assertTrue(defaults.getAllowedContentTypes().containsAll(format.contentTypes()), format.name());
        }
    }
}
