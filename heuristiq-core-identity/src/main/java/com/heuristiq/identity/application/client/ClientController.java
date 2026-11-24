package com.heuristiq.uam.application.client;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.application.client.UpsertClientRequest;
import com.heuristiq.uam.application.client.ApiClientService;
import com.heuristiq.uam.enums.UamEnums.ApiClientType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam/clients")
@Tag(name = "API Clients", description = "OAuth2/API clients (client_id + secret/JWKS) used for machine-to-machine and application authentication.")
public class ClientController {
    private final ApiClientServiceUseCase clientService;
    private final CommonRequestMapper requestMapper;

    public ClientController(ApiClientServiceUseCase clientService, CommonRequestMapper requestMapper) {
        this.clientService = clientService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Register an API client",
            description = "Creates a new OAuth2/API client. The request payload is an UpsertClientRequest carried inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Client id already exists"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertClientRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertClientRequest request = requestMapper.requireRequestObject(commonRequest, UpsertClientRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                clientService.create(new ApiClientServiceUseCase.CreateInput(request)).created()));
    }

    @Operation(summary = "Update an API client",
            description = "Replaces the mutable fields of an existing client. Does not rotate the client secret -- see rotate-secret. The request payload is an UpsertClientRequest carried inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Client not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertClientRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Client id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertClientRequest request = requestMapper.requireRequestObject(commonRequest, UpsertClientRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                clientService.update(new ApiClientServiceUseCase.UpdateInput(id, request)).client()));
    }

    @Operation(summary = "Rotate a client's secret",
            description = "Generates and stores a new client secret, invalidating the previous one. The new secret is returned only in this response and cannot be retrieved again afterward.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Secret rotated, new secret returned"),
            @ApiResponse(responseCode = "404", description = "Client not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<CommonResponseObject> rotateSecret(@Parameter(description = "Client id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                clientService.rotateSecret(new ApiClientServiceUseCase.RotateSecretInput(id)).secret()));
    }

    @Operation(summary = "Get a client by id", description = "Fetches a single API client's details (secret is not included).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client found"),
            @ApiResponse(responseCode = "404", description = "Client not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Client id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                clientService.get(new ApiClientServiceUseCase.GetInput(id)).client()));
    }

    @Operation(summary = "List API clients", description = "Returns a page of registered API clients.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of clients returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
                               @Parameter(description = "Filter by exact client id") @RequestParam(required = false) String clientId,
                               @Parameter(description = "Filter by client type") @RequestParam(required = false) ApiClientType clientType,
                               @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(CommonResponseObject.success(
                clientService.list(new ApiClientServiceUseCase.ListPageInput(page, size, clientId, clientType, active)).clients()));
    }

    @Operation(summary = "Delete an API client", description = "Permanently removes a client record, immediately invalidating its ability to authenticate.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Client deleted"),
            @ApiResponse(responseCode = "404", description = "Client not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "Client id") @PathVariable String id) {
        clientService.delete(new ApiClientServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }
}
