package com.railcab.audit.adapter.in.web;

import static com.railcab.audit.adapter.in.web.WebConstants.*;
import com.railcab.audit.application.AuditEventInput;
import com.railcab.audit.application.AuditIngestService;
import com.railcab.audit.common.CommonResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service ingest (header {@code X-Rider-Service-Token}, or a SERVICE bearer token). Used by the API
 * gateway, NMS and the payment service. Not routed by the gateway to outside callers.
 */
@RestController
@RequestMapping(INTERNAL_BASE)
@Tag(name = "Internal", description = "Used by the gateway, NMS and the payment service to report audit events.")
public class InternalAuditController {

    private final AuditIngestService ingest;

    /**
     * Creates the controller.
     *
     * @param ingest ingest service
     */
    public InternalAuditController(AuditIngestService ingest) {
        this.ingest = ingest;
    }

    /** Request body of the batch endpoint. */
    @Schema(description = "A batch of 1 to 500 events.")
    public record EventBatch(List<AuditEventInput> events) {
    }

    @Operation(summary = "Ingest a batch of audit events",
            description = "Up to 500 events. Each event is validated and masked on its own; valid ones are stored even "
                    + "when others are rejected. Idempotent on eventId, so a producer can safely retry a batch.")
    @PostMapping(value = INTERNAL_EVENTS, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommonResponseObject> events(@RequestBody EventBatch batch) {
        return ResponseEntity.ok(CommonResponseObject.success(ingest.ingestInternal(batch == null ? null : batch.events())));
    }
}
