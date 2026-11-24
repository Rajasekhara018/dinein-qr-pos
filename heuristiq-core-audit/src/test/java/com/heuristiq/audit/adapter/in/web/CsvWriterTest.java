package com.heuristiq.audit.adapter.in.web;

import com.heuristiq.audit.application.AuditEventView;
import com.heuristiq.audit.application.AuditPayloadView;
import com.heuristiq.audit.domain.AuditEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    private static AuditEventView view(long id, String eventId, String userId, String path, String userAgent, List<String> roles) {
        return new AuditEventView(id, eventId, AuditEventType.ACCESS, null, null, null, userId, null, null, roles, null,
                null, null, null, null, path, null, null, null, null, null, null, userAgent, null, null, null, "DOCUMENT", "doc_1", "VIRUS_DETECTED");
    }

    @Test
    void writesHeaderAndRowsWithQuotingAndFormulaNeutralisation() {
        AuditEventView e = view(1L, "evt-1", "=cmd|' /C calc'!A0", "/a,b", "say \"hi\"", List.of("A", "B"));
        String[] lines = CsvWriter.write(List.of(e)).split("\r\n");
        assertThat(lines[0]).startsWith("id,eventId,eventType,occurredAt").endsWith(",details,subjectType,subjectId,reasonCode");
        assertThat(lines[1]).endsWith(",DOCUMENT,doc_1,VIRUS_DETECTED");
        assertThat(lines[1]).contains("'=cmd|' /C calc'!A0").contains("\"/a,b\"").contains("\"say \"\"hi\"\"\"").contains("A|B");
    }

    @Test
    void payloadColumnsAreAppendedOnlyForThePayloadExport() {
        AuditEventView e = view(2L, "evt-2", null, null, null, null);
        assertThat(CsvWriter.write(List.of(e)).split("\r\n")[0]).doesNotContain("requestBody");
        AuditPayloadView p = new AuditPayloadView("evt-2", Map.of("Accept", "*/*"), null, "{\"a\":1}", null, true, false,
                null, null, "application/json");
        String[] lines = CsvWriter.writeWithPayloads(List.of(e), Map.of("evt-2", p)).split("\r\n");
        assertThat(lines[0]).contains(",details,requestHeaders,responseHeaders,requestBody,responseBody,requestBodyTruncated,"
                + "responseBodyTruncated,requestBodySize,responseBodySize,bodyContentType,subjectType,subjectId,reasonCode");
        assertThat(lines[1]).contains("Accept=*/*").contains("\"{\"\"a\"\":1}\"").contains("application/json");
        String noPayload = CsvWriter.writeWithPayloads(List.of(e), Map.of()).split("\r\n")[1];
        assertThat(noPayload).endsWith(",,,,,,,,,,DOCUMENT,doc_1,VIRUS_DETECTED");
    }
}
