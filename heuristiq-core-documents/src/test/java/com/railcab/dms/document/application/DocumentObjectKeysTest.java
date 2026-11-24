package com.railcab.dms.document.application;

import com.railcab.dms.exception.DmsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentObjectKeysTest {

    @Test
    void buildsTenantCategoryTypeOwnerFileShape() {
        assertEquals("org-a/kyc/PHOTO_ID/user-1/ab12cd34_id.pdf",
                DocumentObjectKeys.build("org-a", "kyc", "PHOTO_ID", "user-1", "ab12cd34_id.pdf"));
    }

    @Test
    void noOrganizationAndNoTypeUseFixedSegments() {
        assertEquals("no-organization", DocumentObjectKeys.tenantSegment(null));
        assertEquals("no-organization", DocumentObjectKeys.tenantSegment("  "));
        assertEquals("general", DocumentObjectKeys.documentTypeSegment(null));
        assertEquals("general", DocumentObjectKeys.documentTypeSegment(""));
        assertEquals("PHOTO_ID", DocumentObjectKeys.documentTypeSegment("PHOTO_ID"));
    }

    @Test
    void anOrganizationCannotImpersonateTheNoOrganizationSegment() {
        assertThrows(DmsException.class, () -> DocumentObjectKeys.tenantSegment("no-organization"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../etc", "a/../../b", "....", ".", "", "   ", "a..b"})
    void traversalAndEmptySegmentsAreRejected(String value) {
        assertThrows(DmsException.class, () -> DocumentObjectKeys.safePathPart(value));
    }

    @Test
    void nullIsRejected() {
        assertThrows(DmsException.class, () -> DocumentObjectKeys.safePathPart(null));
    }

    @Test
    void separatorsBackslashesAndUnicodeBecomeUnderscores() {
        assertEquals("a_b", DocumentObjectKeys.safePathPart("a/b"));
        assertEquals("a_b", DocumentObjectKeys.safePathPart("a\\b"));
        assertEquals("_etc_passwd", DocumentObjectKeys.safePathPart("/etc/passwd"));
        assertEquals("____", DocumentObjectKeys.safePathPart("日本語é"));
        assertEquals("a_b", DocumentObjectKeys.safePathPart("a b"));
        assertEquals("a_b", DocumentObjectKeys.safePathPart("a\u0000b"));
    }

    @Test
    void windowsStyleTraversalNeverSurvives() {
        // "..\\..\\x": backslashes are separators, so ".." survives cleaning and is refused.
        assertThrows(DmsException.class, () -> DocumentObjectKeys.safePathPart("..\\..\\x"));
        assertThrows(DmsException.class, () -> DocumentObjectKeys.safePathPart("C:\\..\\x"));
    }

    @Test
    void veryLongSegmentsAreRefusedNotTruncated() {
        assertEquals(128, DocumentObjectKeys.safePathPart("a".repeat(128)).length());
        assertThrows(DmsException.class, () -> DocumentObjectKeys.safePathPart("a".repeat(129)));
    }

    @Test
    void everyBuiltKeyHasExactlyFiveCleanSegments() {
        for (String hostile : new String[]{"x/../y", "a\\b", "日本", "a b", "..a", "a.."}) {
            String tenant;
            String owner;
            try {
                tenant = DocumentObjectKeys.tenantSegment(hostile);
                owner = DocumentObjectKeys.safePathPart(hostile);
            } catch (DmsException rejected) {
                continue;
            }
            String key = DocumentObjectKeys.build(tenant, "cat", "general", owner, DocumentObjectKeys.safeFileName(hostile));
            assertEquals(5, key.split("/", -1).length, key);
            assertFalse(key.contains(".."), key);
            assertFalse(key.contains("\\"), key);
            assertTrue(key.matches("[A-Za-z0-9._/-]+"), key);
        }
    }

    @Test
    void fileNamesAreSanitisedLowerCasedAndBounded() {
        assertEquals("profile_photo.jpg", DocumentObjectKeys.safeFileName("Profile Photo.JPG"));
        assertEquals("document", DocumentObjectKeys.safeFileName(null));
        assertEquals("document", DocumentObjectKeys.safeFileName(".."));
        assertEquals("document", DocumentObjectKeys.safeFileName("a..b.pdf"));
        assertEquals("document", DocumentObjectKeys.safeFileName("../../evil.pdf"));
        String longName = "x".repeat(300) + ".pdf";
        String safe = DocumentObjectKeys.safeFileName(longName);
        assertEquals(120, safe.length());
        assertTrue(safe.endsWith(".pdf"));
    }
}
