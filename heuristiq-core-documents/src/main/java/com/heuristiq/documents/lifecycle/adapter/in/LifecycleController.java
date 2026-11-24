package com.heuristiq.documents.lifecycle.adapter.in;

import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.document.persistence.DocumentSearchCriteria;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.lifecycle.application.LifecycleQueryService;
import com.heuristiq.documents.security.DmsSecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Read-only current state of documents for troubleshooting (events live in the central audit service). Deliberately not under {@code /documents}, whose
 * {@code /{*path}} download route matches everything. The organization is always the session's, never a request value.
 */
@RestController
@RequestMapping("/api/v1/dms/lifecycle")
@Tag(name = "Document lifecycle", description = "Search documents and see their current state. Read only; limited to the caller's organization.")
public class LifecycleController {

    private final LifecycleQueryService queryService;

    public LifecycleController(LifecycleQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Search documents",
            description = "Documents of the caller's organization, deleted ones included unless filtered, newest first. "
                    + "'query' matches the id, part of the file name, or the start of the checksum, literally. Page size is capped at 100.")
    @GetMapping("/documents")
    public ResponseEntity<CommonResponseObject> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "ownerId", required = false) String ownerId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scanStatus", required = false) String scanStatus,
            @RequestParam(value = "deleted", required = false) Boolean deleted,
            @Parameter(description = "ISO-8601 instant; createdAt at or after") @RequestParam(value = "from", required = false) String from,
            @Parameter(description = "ISO-8601 instant; createdAt at or before") @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        DocumentSearchCriteria criteria = new DocumentSearchCriteria(blankToNull(query), blankToNull(ownerId),
                blankToNull(category), blankToNull(documentType), blankToNull(status), blankToNull(scanStatus), deleted,
                parseInstant("from", from), parseInstant("to", to));
        return ResponseEntity.ok(CommonResponseObject.success(LifecycleMapper.toPage(
                queryService.searchDocuments(tenant(), criteria, page, size), LifecycleMapper::toRow)));
    }

    @Operation(summary = "Document detail", description = "One document of the caller's organization, deleted or not. "
            + "A document of another organization answers 404, like a missing one.")
    @GetMapping("/documents/{id}")
    public ResponseEntity<CommonResponseObject> detail(@PathVariable("id") String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                LifecycleMapper.toDetail(queryService.getDocument(tenant(), id))));
    }

    /** The session's organization; never a request value. A missing one is rejected by the service (400). */
    private static String tenant() {
        return DmsSecurityContext.currentPrincipal().tenantId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant parseInstant(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw DmsException.badRequest(name + " must be an ISO-8601 instant such as 2026-03-01T00:00:00Z");
        }
    }
}
