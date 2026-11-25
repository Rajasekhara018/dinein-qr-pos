package com.heuristiq.documents.documenttype.adapter.in;

import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.documenttype.application.DocumentTypeApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service callback of Rider_WorkflowService. Reachable only with a service credential (see
 * {@code InternalServiceAuthFilter}); the gateway refuses the whole {@code /api/v1/dms/internal/} prefix. Not in the
 * privilege catalog on purpose.
 */
@RestController
@RequestMapping("/api/v1/dms/internal/document-types")
@Tag(name = "Internal", description = "Used by Rider_WorkflowService after a change request is approved.")
public class InternalDocumentTypeController {

    private final DocumentTypeApplyService applyService;

    public InternalDocumentTypeController(DocumentTypeApplyService applyService) {
        this.applyService = applyService;
    }

    @Operation(summary = "Apply an approved document type change",
            description = "200 applied (also for a repeat of the same change request), 403 maker approved their own " +
                    "change, 409 the record changed since the request was made (stale), 400 malformed.")
    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommonResponseObject> apply(@RequestBody String body) {
        return ResponseEntity.ok(CommonResponseObject.success(applyService.apply(body)));
    }
}
