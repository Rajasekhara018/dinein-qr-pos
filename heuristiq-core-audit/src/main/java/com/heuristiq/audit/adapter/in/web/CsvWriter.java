package com.heuristiq.audit.adapter.in.web;

import static com.heuristiq.audit.adapter.in.web.WebConstants.CSV_FORMULA_STARTS;
import static com.heuristiq.audit.adapter.in.web.WebConstants.CSV_HEADER;
import static com.heuristiq.audit.adapter.in.web.WebConstants.CSV_LINE_END;
import static com.heuristiq.audit.adapter.in.web.WebConstants.CSV_LIST_SEPARATOR;
import static com.heuristiq.audit.adapter.in.web.WebConstants.CSV_PAIR_SEPARATOR;
import static com.heuristiq.audit.adapter.in.web.WebConstants.CSV_TRAILING_HEADER;
import static com.heuristiq.audit.adapter.in.web.WebConstants.PAYLOAD_HEADER;

import com.heuristiq.audit.application.AuditEventView;
import com.heuristiq.audit.application.AuditPayloadView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders events as RFC 4180 CSV. Cells that start with a spreadsheet formula character are prefixed with an
 * apostrophe so an exported file can not run a formula when opened in Excel (CSV injection).
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    /**
     * @param events the rows
     * @return the CSV text with a header line
     */
    public static String write(List<AuditEventView> events) {
        return render(events, null);
    }

    /**
     * @param events   the rows
     * @param payloads stored payloads by eventId
     * @return the CSV text with the base columns followed by the payload columns
     */
    public static String writeWithPayloads(List<AuditEventView> events, Map<String, AuditPayloadView> payloads) {
        return render(events, payloads == null ? Map.of() : payloads);
    }

    private static String render(List<AuditEventView> events, Map<String, AuditPayloadView> payloads) {
        StringBuilder sb = new StringBuilder(64 * (events.size() + 1));
        sb.append(String.join(",", CSV_HEADER));
        if (payloads != null) {
            sb.append(',').append(String.join(",", PAYLOAD_HEADER));
        }
        sb.append(',').append(String.join(",", CSV_TRAILING_HEADER)).append(CSV_LINE_END);
        for (AuditEventView e : events) {
            List<String> cells = new ArrayList<>(List.of(str(e.id()), str(e.eventId()), str(e.eventType()),
                    str(e.occurredAt()), str(e.sourceService()), str(e.userId()), str(e.userType()), str(e.role()),
                    e.roles() == null ? "" : String.join(CSV_LIST_SEPARATOR, e.roles()),
                    e.privilegeIds() == null ? "" : e.privilegeIds().stream().map(String::valueOf).collect(Collectors.joining(CSV_LIST_SEPARATOR)),
                    str(e.feature()), str(e.module()), str(e.screenKey()), str(e.method()), str(e.path()),
                    str(e.statusCode()), str(e.outcome()), str(e.durationMs()), str(e.correlationId()),
                    str(e.requestId()), str(e.ipAddress()), str(e.userAgent()), str(e.appChannel()),
                    str(e.tenantId()), details(e.details())));
            if (payloads != null) {
                AuditPayloadView p = payloads.get(e.eventId());
                if (p == null) {
                    cells.addAll(Collections.nCopies(PAYLOAD_HEADER.size(), ""));
                } else {
                    cells.addAll(List.of(details(p.requestHeaders()), details(p.responseHeaders()),
                            str(p.requestBody()), str(p.responseBody()), str(p.requestBodyTruncated()),
                            str(p.responseBodyTruncated()), str(p.requestBodySize()), str(p.responseBodySize()),
                            str(p.bodyContentType())));
                }
            }
            cells.addAll(List.of(str(e.subjectType()), str(e.subjectId()), str(e.reasonCode())));
            sb.append(cells.stream().map(CsvWriter::cell).collect(Collectors.joining(","))).append(CSV_LINE_END);
        }
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String details(Map<String, String> details) {
        return details == null ? "" : details.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(CSV_PAIR_SEPARATOR));
    }

    /**
     * @param raw a cell value
     * @return the value quoted and escaped for CSV, neutralised against formula injection
     */
    static String cell(String raw) {
        String v = raw;
        if (!v.isEmpty() && CSV_FORMULA_STARTS.indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
