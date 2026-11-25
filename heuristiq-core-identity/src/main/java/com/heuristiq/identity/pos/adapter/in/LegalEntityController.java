package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.pos.application.PosCaller;
import com.heuristiq.identity.pos.application.PosOrganizationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An organization's legal entities (the companies that issue invoices, one country each). Privileges
 * {@code POS_LEGAL_ENTITY_*} at the gateway; the service answers "not found" for another organization's data.
 */
@RestController
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@RequestMapping("/api/v1/uam/organizations/{orgId}/legal-entities")
@Tag(name = "POS legal entities", description = "An organization's legal entities.")
public class LegalEntityController {

    private final PosOrganizationUseCase pos;
    private final CommonRequestMapper requestMapper;
    private final PosCallers callers;

    LegalEntityController(PosOrganizationUseCase pos, CommonRequestMapper requestMapper, PosCallers callers) {
        this.pos = pos;
        this.requestMapper = requestMapper;
        this.callers = callers;
    }

    @Operation(summary = "List an organization's legal entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal entities returned"),
            @ApiResponse(responseCode = "404", description = "Organization not found or not reachable"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Organization id") @PathVariable String orgId) {
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return pos.legalEntities(caller, orgId).stream().map(LegalEntityResponse::from).toList();
        })));
    }

    @Operation(summary = "Get a legal entity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal entity returned"),
            @ApiResponse(responseCode = "404", description = "Not found or not reachable"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                    @Parameter(description = "Legal entity id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return LegalEntityResponse.from(pos.legalEntity(caller, orgId, id));
        })));
    }

    @Operation(summary = "Create a legal entity", description = "The answer carries version 1. Request payload is a LegalEntityRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal entity created"),
            @ApiResponse(responseCode = "400", description = "Validation failed: message is a pos.* key, errorCode the field"),
            @ApiResponse(responseCode = "404", description = "Organization not found or not reachable"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(LegalEntityRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                       @Valid @RequestBody CommonRequestObject commonRequest) {
        LegalEntityRequest request = requestMapper.requireRequestObject(commonRequest, LegalEntityRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return LegalEntityResponse.from(pos.createLegalEntity(caller, orgId, request.toDraft()));
        })));
    }

    @Operation(summary = "Change a legal entity",
            description = "Send the version read with the GET. Request payload is a LegalEntityRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal entity saved; the new state is returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed: message is a pos.* key, errorCode the field"),
            @ApiResponse(responseCode = "404", description = "Not found or not reachable"),
            @ApiResponse(responseCode = "409", description = "pos.staleVersion or another clash with the current state"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(LegalEntityRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Organization id") @PathVariable String orgId,
                                                       @Parameter(description = "Legal entity id") @PathVariable String id,
                                                       @Valid @RequestBody CommonRequestObject commonRequest) {
        LegalEntityRequest request = requestMapper.requireRequestObject(commonRequest, LegalEntityRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(PosErrors.translated(() -> {
            PosCaller caller = callers.current(orgId);
            return LegalEntityResponse.from(pos.updateLegalEntity(caller, orgId, id, request.toDraft(),
                    PosErrors.requireVersion(request.version())));
        })));
    }
}
