package com.railcab.dms.document.persistence.mongo;

import com.railcab.dms.document.persistence.DocumentSearchCriteria;
import com.railcab.dms.document.persistence.SearchPatterns;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The MongoDB query always starts from the organization, and free text never becomes a regex. */
class MongoDocumentSearchQueriesTest {

    private static DocumentSearchCriteria only(String query) {
        return new DocumentSearchCriteria(query, null, null, null, null, null, null, null, null);
    }

    private static String json(String tenant, DocumentSearchCriteria criteria) {
        Query query = MongoDocumentSearchQueries.build(tenant, criteria);
        return query.getQueryObject().toJson();
    }

    @Test
    void organizationIsAlwaysTheFirstCriterion() {
        String json = json("org-a", only(null));

        assertTrue(json.contains("\"tenantId\": \"org-a\""), json);
        assertTrue(json.startsWith("{\"$and\": [{\"tenantId\": \"org-a\"}"), json);
    }

    @Test
    void anotherOrganizationNeverAppearsInTheQuery() {
        assertFalse(json("org-a", new DocumentSearchCriteria("x", "u1", "kyc", "PHOTO_ID", "UPLOADED", "CLEAN", false, null, null))
                .contains("org-b"));
    }

    @Test
    void everyFilterIsAnExactMatchOnItsOwnField() {
        String json = json("org-a", new DocumentSearchCriteria(null, "u1", "kyc", "PHOTO_ID", "UPLOADED", "CLEAN", true, null, null));

        for (String expected : new String[]{"\"ownerId\": \"u1\"", "\"category\": \"kyc\"", "\"documentType\": \"PHOTO_ID\"",
                "\"status\": \"UPLOADED\"", "\"scanStatus\": \"CLEAN\"", "\"deleted\": true"}) {
            assertTrue(json.contains(expected), expected + " in " + json);
        }
    }

    @Test
    void dateRangeFiltersCreatedAt() {
        Query query = MongoDocumentSearchQueries.build("org-a", new DocumentSearchCriteria(null, null, null, null, null, null,
                null, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z")));
        String text = query.getQueryObject().toString();

        assertTrue(text.contains("createdAt"), text);
        assertTrue(text.contains("$gte") && text.contains("$lte"), text);
    }

    @Test
    void freeTextIsEscapedSoRegexMetacharactersAreLiteral() {
        String json = json("org-a", only(".*(a|b)+[x]"));

        assertTrue(json.contains("\\\\.\\\\*\\\\(a\\\\|b\\\\)\\\\+\\\\[x\\\\]"), json);
        assertFalse(json.contains("\"$regex\": \".*"), json);
    }

    @Test
    void textSearchCoversIdFileNameAndChecksumPrefix() {
        String json = json("org-a", only("ABC"));

        assertTrue(json.contains("$or"), json);
        assertTrue(json.contains("originalFileName"), json);
        assertTrue(json.contains("checksumSha256"), json);
        assertTrue(json.contains("\"^abc\"") || json.contains("^abc"), json);
    }

    @Test
    void escapeRegexLeavesLettersDigitsAndNonAsciiAlone() {
        assertEquals("abc123", SearchPatterns.escapeRegex("abc123"));
        assertEquals("a\\.b\\-c", SearchPatterns.escapeRegex("a.b-c"));
        assertEquals("日本", SearchPatterns.escapeRegex("日本"));
        assertEquals("\\\\", SearchPatterns.escapeRegex("\\"));
    }
}
