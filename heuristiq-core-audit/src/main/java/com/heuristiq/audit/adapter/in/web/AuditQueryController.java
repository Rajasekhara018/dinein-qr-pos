package com.railcab.audit.adapter.in.web;

import static com.railcab.audit.adapter.in.web.WebConstants.*;
import com.railcab.audit.application.AuditQueryService;
import com.railcab.audit.application.EventQuery;
import com.railcab.audit.common.CommonResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Read API behind the AdminWeb audit screen. Authorization is privilege-based at the gateway
 * ({@code privilege.json}, service {@code rider-audit}); the service only requires a valid token.
 */
@RestController
@RequestMapping(EVENTS_BASE)
@Tag(name = "Audit events", description = "Search, view, export and filter values of the central audit trail.")
public class AuditQueryController {

    private final AuditQueryService queries;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param queries query service
     * @param clock   time source (names the export file)
     */
    public AuditQueryController(AuditQueryService queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Operation(summary = "Search audit events",
            description = "Newest first. Filters combine with AND; size is capped at 200. subjectType, subjectId and "
                    + "reasonCode are exact matches on what the event is about and why.")
    @GetMapping
    public ResponseEntity<CommonResponseObject> search(
            @RequestParam(required = false) String userId, @RequestParam(required = false) String role,
            @RequestParam(required = false) String feature, @RequestParam(required = false) String screenKey,
            @RequestParam(required = false) String eventType, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String channel, @RequestParam(required = false) Integer privilegeId,
            @RequestParam(required = false) String method, @RequestParam(required = false) String path,
            @RequestParam(required = false) String correlationId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId, @RequestParam(required = false) String reasonCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        EventQuery q = new EventQuery(userId, role, feature, screenKey, eventType, outcome, channel, privilegeId,
                method, path, correlationId, from, to, subjectType, subjectId, reasonCode);
        return ResponseEntity.ok(CommonResponseObject.success(queries.search(CurrentUser.require(), q, page, size)));
    }

    @Operation(summary = "Export audit events as CSV",
            description = "Same filters as the search; the newest matching rows up to the configured cap (10,000 by default).")
    @GetMapping(EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String userId, @RequestParam(required = false) String role,
            @RequestParam(required = false) String feature, @RequestParam(required = false) String screenKey,
            @RequestParam(required = false) String eventType, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String channel, @RequestParam(required = false) Integer privilegeId,
            @RequestParam(required = false) String method, @RequestParam(required = false) String path,
            @RequestParam(required = false) String correlationId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId, @RequestParam(required = false) String reasonCode) {
        EventQuery q = new EventQuery(userId, role, feature, screenKey, eventType, outcome, channel, privilegeId,
                method, path, correlationId, from, to, subjectType, subjectId, reasonCode);
        byte[] body = CsvWriter.write(queries.exportEvents(CurrentUser.require(), q)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        CSV_ATTACHMENT_PREFIX + CSV_FILE_PREFIX + LocalDate.now(clock) + CSV_ATTACHMENT_SUFFIX)
                .contentType(new MediaType(CSV_TYPE, CSV_SUBTYPE, StandardCharsets.UTF_8)).body(body);
    }

    @Operation(summary = "Export audit events with their headers and bodies as CSV",
            description = "Needs the separate payload-export privilege. Bodies are redacted but may still hold personal "
                    + "data. Capped at 1,000 rows by default.")
    @GetMapping(EXPORT_PAYLOADS)
    public ResponseEntity<byte[]> exportPayloads(
            @RequestParam(required = false) String userId, @RequestParam(required = false) String role,
            @RequestParam(required = false) String feature, @RequestParam(required = false) String screenKey,
            @RequestParam(required = false) String eventType, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String channel, @RequestParam(required = false) Integer privilegeId,
            @RequestParam(required = false) String method, @RequestParam(required = false) String path,
            @RequestParam(required = false) String correlationId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId, @RequestParam(required = false) String reasonCode) {
        EventQuery q = new EventQuery(userId, role, feature, screenKey, eventType, outcome, channel, privilegeId,
                method, path, correlationId, from, to, subjectType, subjectId, reasonCode);
        byte[] body = toCsv(queries.exportPayloads(CurrentUser.require(), q)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        CSV_ATTACHMENT_PREFIX + CSV_PAYLOAD_FILE_PREFIX + LocalDate.now(clock) + CSV_ATTACHMENT_SUFFIX)
                .contentType(new MediaType(CSV_TYPE, CSV_SUBTYPE, StandardCharsets.UTF_8)).body(body);
    }

    @Operation(summary = "Filter values", description = "Distinct roles, features, screen keys, event types, outcomes "
            + "channels, subject types and reason codes seen in the recent window, for the screen's dropdowns.")
    @GetMapping(FACETS)
    public ResponseEntity<CommonResponseObject> facets() {
        return ResponseEntity.ok(CommonResponseObject.success(queries.facets(CurrentUser.require())));
    }

    @Operation(summary = "Events of one request chain", description = "Everything recorded under a correlation id, oldest first.")
    @GetMapping(BY_CORRELATION)
    public ResponseEntity<CommonResponseObject> byCorrelation(@PathVariable String correlationId) {
        return ResponseEntity.ok(CommonResponseObject.success(queries.byCorrelation(CurrentUser.require(), correlationId)));
    }

    @Operation(summary = "Headers and bodies of one audit event",
            description = "Redacted request/response headers and bodies. Needs the separate payload privilege, which is "
                    + "why they are not part of the plain event view. May still contain personal data.")
    @GetMapping(PAYLOAD)
    public ResponseEntity<CommonResponseObject> payload(@PathVariable long id) {
        return ResponseEntity.ok(CommonResponseObject.success(queries.payload(CurrentUser.require(), id)));
    }

    @Operation(summary = "One audit event", description = "The event without its headers and bodies; see /payload.")
    @GetMapping(BY_ID)
    public ResponseEntity<CommonResponseObject> get(@PathVariable long id) {
        return ResponseEntity.ok(CommonResponseObject.success(queries.get(CurrentUser.require(), id)));
    }

    private static String toCsv(AuditQueryService.PayloadExport export) {
        return CsvWriter.writeWithPayloads(export.events(), export.payloads());
    }
}
