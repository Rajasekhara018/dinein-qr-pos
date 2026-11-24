package com.heuristiq.payment.payment.controller;

import com.heuristiq.payment.payment.PaymentConstants;
import com.heuristiq.payment.payment.dto.DirectCollectionRequest;
import com.heuristiq.payment.payment.service.DirectCollectionService;
import com.heuristiq.payment.shared.dto.ResponseObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service (X-Rider-Service-Token, see SecurityConfig; never routed by the gateway): Rider_RideService records each
 * direct collection, a cash or UPI fare the rider paid to the driver (docs/DRIVER_FRANCHISE_SUBSCRIPTION_PLAN.md, D13).
 */
@RestController
@RequiredArgsConstructor
public class InternalDirectCollectionController {

    private final DirectCollectionService directCollections;

    @Operation(summary = "Record a direct collection (cash or UPI paid to the driver)",
            description = "Stores a CAPTURED payment (method CASH, or UPI_DIRECT for channel UPI) with a receipt, in the organization of "
                    + "the X-Tenant-Id header (required). No gateway is called and no driver earnings are computed (the ledger books "
                    + "them). One record per ride: a repeat answers 200 with the stored payment; 201 when recorded now. 400 for an "
                    + "invalid body, an amount in another currency or no organization; 409 when the ride's collection belongs to "
                    + "another organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recorded"),
            @ApiResponse(responseCode = "200", description = "Recorded before for this ride"),
            @ApiResponse(responseCode = "400", description = "Invalid request or no organization"),
            @ApiResponse(responseCode = "409", description = "Recorded for another organization")
    })
    @PostMapping(PaymentConstants.DIRECT_COLLECTIONS_PATH)
    public ResponseEntity<ResponseObject> record(@Valid @RequestBody DirectCollectionRequest request) {
        DirectCollectionService.Recorded recorded = directCollections.record(request);
        return ResponseEntity.status(recorded.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ResponseObject.builder().status(true).object(recorded.payment()).build());
    }
}
