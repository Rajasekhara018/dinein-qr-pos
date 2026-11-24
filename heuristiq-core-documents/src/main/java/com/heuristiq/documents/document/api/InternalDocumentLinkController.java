package com.railcab.dms.document.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.railcab.dms.common.dto.CommonResponseObject;
import com.railcab.dms.document.application.InternalDocumentLinkService;
import com.railcab.dms.document.application.InternalLinkConstants;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A download link for another platform service. Reachable only with a service credential ({@code X-Rider-Service-Token} or a
 * SERVICE-role JWT, see {@code InternalServiceAuthFilter}); the gateway refuses the {@code /api/v1/dms/internal/} prefix, so it is
 * not in the privilege catalog. The organization comes from the required {@code X-Tenant-Id} header and must own the document.
 */
@RestController
@Tag(name = "Internal", description = "Document links for platform services.")
public class InternalDocumentLinkController {

    private final InternalDocumentLinkService links;

    public InternalDocumentLinkController(InternalDocumentLinkService links) {
        this.links = links;
    }

    @Operation(summary = "Download link of a document of the caller's organization (internal)",
            description = "A pre-signed storage URL, or the DMS public-token path, with the document's content type and size.")
    @ApiResponse(responseCode = "200", description = "Link issued")
    @ApiResponse(responseCode = "400", description = "X-Tenant-Id missing")
    @ApiResponse(responseCode = "401", description = "No valid service credential")
    @ApiResponse(responseCode = "404", description = "No such document in that organization (or deleted)")
    @PostMapping(InternalLinkConstants.LINK_PATH)
    public ResponseEntity<CommonResponseObject> link(@PathVariable String id,
                                                     @RequestHeader(value = InternalLinkConstants.TENANT_HEADER, required = false)
                                                     String tenantId) {
        return ResponseEntity.ok(CommonResponseObject.success(links.link(tenantId, id)));
    }
}
