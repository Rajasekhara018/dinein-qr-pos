package com.heuristiq.documents.expiry.adapter.in;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.expiry.application.DocumentExpiryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Which drivers have a lapsed document (D12), for Rider_DispatchService: it refuses to put them online and keeps them out of new
 * offers. Reachable only with a service credential ({@code X-Rider-Service-Token} or a SERVICE-role JWT, see
 * {@code InternalServiceAuthFilter}); the gateway refuses the whole {@code /api/v1/dms/internal/} prefix, so it is not in the
 * privilege catalog. The organization is required in the body. A read sent as POST because the owner list can be long.
 */
@RestController
@Tag(name = "Internal", description = "Document expiry facts for Rider_DispatchService.")
public class InternalDocumentValidityController {

    private final DocumentExpiryService expiry;

    public InternalDocumentValidityController(DocumentExpiryService expiry) {
        this.expiry = expiry;
    }

    /** The organization and the drivers (document owners) to check. */
    public record LapsedQuery(String tenantId, List<String> ownerIds) {
    }

    /** By driver, the tracked categories that have lapsed (e.g. {@code driver-insurance}); drivers with none are left out. */
    public record LapsedAnswer(Map<String, Set<String>> lapsed) {
    }

    @Operation(summary = "Drivers with a lapsed document (internal)",
            description = "For each driver, the tracked document categories (licence, RC, insurance, PUC, permit) whose counting date is "
                    + "before today: the latest reviewer-confirmed date, else the latest date the driver entered.")
    @ApiResponse(responseCode = "200", description = "The lapsed categories by driver")
    @ApiResponse(responseCode = "400", description = "tenantId missing, or no or too many ownerIds")
    @ApiResponse(responseCode = "401", description = "No valid service credential")
    @PostMapping(ExpiryWebConstants.LAPSED_PATH)
    public ResponseEntity<CommonResponseObject> lapsed(@RequestBody LapsedQuery query) {
        return ResponseEntity.ok(CommonResponseObject.success(new LapsedAnswer(expiry.lapsed(query.tenantId(), query.ownerIds()))));
    }
}
