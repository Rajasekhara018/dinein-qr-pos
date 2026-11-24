package com.railcab.dms.documenttype.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.dms.documenttype.application.CallerContext;
import com.railcab.dms.documenttype.domain.DocumentTypeFields;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test data builders shared by the document-type tests. */
public final class DocumentTypeFixtures {

    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String ORG_A = "org-a";
    public static final String ORG_B = "org-b";

    private DocumentTypeFixtures() {
    }

    public static CallerContext caller(String tenantId, String userId) {
        return new CallerContext(userId, tenantId, "token-of-" + userId);
    }

    public static DocumentTypeFields fields(String code) {
        return new DocumentTypeFields(code, "matrimony-documents", "MATRIMONY_ONBOARDING",
                Map.of("en", "Photo ID", "hi", "फोटो पहचान पत्र"), List.of("PDF", "JPG"), 5, true, 10, true);
    }

    /** The JSON body Workflow posts back after an approval, built from what the maker submitted. */
    public static String applyPayload(FakeChangeRequestGateway.Submission submission, String changeRequestId, String approvedBy,
                                      String subjectType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("changeRequestId", changeRequestId);
            payload.put("subjectType", subjectType);
            payload.put("subjectId", submission.subjectId());
            payload.put("before", submission.before());
            payload.put("after", submission.after());
            payload.put("beforeHash", com.railcab.dms.documenttype.application.CanonicalJson.sha256(
                    MAPPER.valueToTree(submission.before())));
            payload.put("approvedBy", approvedBy);
            payload.put("approvedAt", "2026-01-01T00:00:00Z");
            return MAPPER.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
