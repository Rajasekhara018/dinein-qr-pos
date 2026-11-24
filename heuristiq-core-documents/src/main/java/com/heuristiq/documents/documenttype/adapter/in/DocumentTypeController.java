package com.heuristiq.documents.documenttype.adapter.in;

import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.documenttype.application.CallerContext;
import com.heuristiq.documents.documenttype.application.DocumentTypeFileCheckService;
import com.heuristiq.documents.documenttype.application.DocumentTypeQueryService;
import com.heuristiq.documents.documenttype.application.DocumentTypeSubmitService;
import com.heuristiq.documents.documenttype.application.SubmissionResult;
import com.heuristiq.documents.security.DmsSecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Organization-level document types. Reads are immediate; creating or changing a type is a proposal that goes to the
 * approval service (maker-checker) and only takes effect after another user approves it. The organization is always
 * the caller's session organization, never a request value.
 */
@RestController
@RequestMapping("/api/v1/dms/document-types")
@Tag(name = "Document types", description = "Which documents an organization accepts, and the rule for each (formats, " +
        "size). Changes are proposed here and applied after approval in the workflow service.")
public class DocumentTypeController {

    private final DocumentTypeQueryService queryService;
    private final DocumentTypeSubmitService submitService;
    private final DocumentTypeFileCheckService fileCheckService;

    public DocumentTypeController(DocumentTypeQueryService queryService, DocumentTypeSubmitService submitService,
                                  DocumentTypeFileCheckService fileCheckService) {
        this.queryService = queryService;
        this.submitService = submitService;
        this.fileCheckService = fileCheckService;
    }

    @Operation(summary = "List document types",
            description = "The caller's organization's types, sorted by code, optionally filtered. Page size is capped at 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of document types.",
                    content = @Content(schema = @Schema(implementation = DocumentTypePageResponse.class))),
            @ApiResponse(responseCode = "400", description = "The session has no organization.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(
            @Parameter(description = "Only this category (case-insensitive).") @RequestParam(required = false) String category,
            @Parameter(description = "Only this flow.") @RequestParam(required = false) String flow,
            @Parameter(description = "Only active (true) or inactive (false) types.") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Zero-based page.") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size, 1-100.") @RequestParam(required = false) Integer size) {
        String tenantId = DmsSecurityContext.currentPrincipal().tenantId();
        return ResponseEntity.ok(CommonResponseObject.success(
                DocumentTypeWebMapper.toPage(queryService.list(tenantId, category, flow, active, page, size))));
    }

    @Operation(summary = "Get a document type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The document type.",
                    content = @Content(schema = @Schema(implementation = DocumentTypeResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such type in the caller's organization.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Document type id.") @PathVariable String id) {
        String tenantId = DmsSecurityContext.currentPrincipal().tenantId();
        return ResponseEntity.ok(CommonResponseObject.success(DocumentTypeWebMapper.toResponse(queryService.get(tenantId, id))));
    }

    @Operation(summary = "Propose a new document type",
            description = "Validates the proposal and submits it for approval. Nothing is stored until a different user " +
                    "approves it in the workflow service.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Submitted for approval.",
                    content = @Content(schema = @Schema(implementation = SubmissionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid fields.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "409", description = "The code already exists in the organization.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "503", description = "The approval service is unavailable; nothing was changed.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody DocumentTypeRequest request) {
        CallerContext caller = CallerContextResolver.resolve(authorization);
        SubmissionResult result = submitService.submitCreate(caller, DocumentTypeWebMapper.toFields(request));
        return accepted(result);
    }

    @Operation(summary = "Propose a change to a document type",
            description = "The code is immutable and ignored. Pass the version you loaded to detect a concurrent change.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Submitted for approval.",
                    content = @Content(schema = @Schema(implementation = SubmissionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid fields.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "No such type in the caller's organization.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "409", description = "The type changed since the supplied version.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "503", description = "The approval service is unavailable; nothing was changed.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(description = "Document type id.") @PathVariable String id,
            @RequestBody DocumentTypeRequest request) {
        CallerContext caller = CallerContextResolver.resolve(authorization);
        SubmissionResult result = submitService.submitUpdate(caller, id, DocumentTypeWebMapper.toFields(request),
                request.version());
        return accepted(result);
    }

    @Operation(summary = "Check a file against a document type",
            description = "Runs the upload checks (size, content type, extension, blocked types and content sniffing) " +
                    "without storing or scanning the file remotely, and reports every reason it would be refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The verdict and the rule applied.",
                    content = @Content(schema = @Schema(implementation = FileCheckResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unknown or inactive document type, or wrong category.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PostMapping(value = "/check-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponseObject> checkFile(
            @Parameter(description = "The sample file.", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "Document type code.", required = true) @RequestParam("documentType") String documentType,
            @Parameter(description = "Optional category; must match the type's category.")
            @RequestParam(value = "category", required = false) String category) {
        String tenantId = DmsSecurityContext.currentPrincipal().tenantId();
        return ResponseEntity.ok(CommonResponseObject.success(
                DocumentTypeWebMapper.toResponse(fileCheckService.check(tenantId, category, documentType, file))));
    }

    private static ResponseEntity<CommonResponseObject> accepted(SubmissionResult result) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CommonResponseObject.success(
                new SubmissionResponse(result.changeRequestId(), result.status())));
    }
}
