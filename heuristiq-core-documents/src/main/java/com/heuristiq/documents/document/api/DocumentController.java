package com.heuristiq.documents.document.api;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.documents.common.dto.CommonRequestObject;
import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.document.application.DocumentService;
import com.heuristiq.documents.document.dto.DocumentDownloadUrlResponse;
import com.heuristiq.documents.document.dto.DocumentResponse;
import com.heuristiq.documents.document.dto.DocumentUploadRequest;
import com.heuristiq.documents.document.dto.LegalHoldRequest;
import com.heuristiq.documents.document.dto.RUpload;
import com.heuristiq.documents.exception.DmsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Validated
@RestController
@RequestMapping(DmsCommonConstants.DOCUMENTS_BASE_PATH)
@Tag(name = "Documents", description = "Upload, retrieve, list, and manage the lifecycle of documents stored by the DMS " +
        "(malware scanning, checksum verification, versioned object storage, legal hold, and audited download).")
public class DocumentController {

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentService documentService, ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.objectMapper = objectMapper;
    }

    @Operation(
            summary = "Upload a document",
            description = "Runs the file through the full ingestion pipeline before it is persisted: (1) validation -- " +
                    "required-field checks, configured max size / allowed content-type / allowed extension for the " +
                    "given category, and, if a checksum was supplied, SHA-256 verification against the uploaded bytes; " +
                    "(2) malware scan -- blocked-extension/content-type rejection, magic-byte content sniffing to catch " +
                    "spoofed file types, and an antivirus scan via the configured provider (skipped only when the file " +
                    "is password-protected, since encrypted content can't be scanned); (3) an owner-access check against " +
                    "the caller's identity; (4) versioned storage of the object with the active storage provider; " +
                    "(5) persistence of document metadata (category, owner, checksum, scan result, retention defaults); " +
                    "and (6) an audit record of the upload. Fails closed at the first failing step."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document accepted, scanned, stored, and its metadata persisted.",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing/empty file, disallowed size/content-type/extension, " +
                    "checksum mismatch, malware scan failure, or blocked/dangerous file type.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the document owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected error while scanning, storing, or persisting the document.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponseObject> upload(
            @Parameter(description = "The file content to upload.", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Logical document category, used to select validation rules and as part of the storage path.",
                    required = true, example = "invoices")
            @RequestParam("category") @NotBlank String category,
            @Parameter(description = "Identifier of the document's owner; used for the storage path and access control.",
                    required = true, example = "user-12345")
            @RequestParam("ownerId") @NotBlank String ownerId,
            @Parameter(description = "Password for password-protected files (e.g. encrypted archives/PDFs). " +
                    "When present, the malware scan step is skipped since encrypted content cannot be scanned.")
            @RequestParam(value = "password", required = false) String password,
            @Parameter(description = "Expected SHA-256 checksum (hex) of the file contents. When supplied, the upload " +
                    "is rejected if the computed checksum does not match.", example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
            @RequestParam(value = "checksum", required = false) String checksum,
            @Parameter(description = "Optional document type code of the caller's organization (see /api/v1/dms/document-types). " +
                    "When given, the type's size limit and allowed formats replace the category rule; the type must be " +
                    "active and belong to the same category.", example = "MATRIMONY_PHOTO_ID")
            @RequestParam(value = "documentType", required = false) String documentType,
            @Parameter(description = "Optional last day the document is valid (ISO yyyy-MM-dd), as read from it (D12: driving licence, "
                    + "RC, insurance, PUC, permit). A date before today is refused. A reviewer confirms it later.", example = "2027-03-31")
            @RequestParam(value = "validUntil", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate validUntil
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponseObject.success(documentService.upload(file, category, ownerId, password, checksum, documentType,
                        validUntil)));
    }

    @Operation(summary = "Get document metadata by id",
            description = "Fetches persisted metadata for a single, non-deleted document, enforcing the caller's owner/privileged-role access.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadata found and returned.",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the document owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "Document metadata not found (or already deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @GetMapping("/metadata/{id}")
    public ResponseEntity<CommonResponseObject> getMetadata(
            @Parameter(description = "Document metadata id.", required = true) @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(documentService.getMetadata(id)));
    }

    @Operation(summary = "Get document metadata in the shared RUpload shape",
            description = "Same lookup and access checks as the plain metadata endpoint, mapped onto the common RUpload " +
                    "shape used elsewhere in the platform (file base64 contents are never populated here).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadata found and returned in RUpload form.",
                    content = @Content(schema = @Schema(implementation = RUpload.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the document owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "Document metadata not found (or already deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @GetMapping("/metadata/{id}/rupload")
    public ResponseEntity<CommonResponseObject> getMetadataAsRUpload(
            @Parameter(description = "Document metadata id.", required = true) @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(documentService.getMetadataAsRUpload(id)));
    }

    @Operation(summary = "List documents for a category/owner",
            description = "Returns non-deleted document metadata for the given category and owner, newest first. Requires " +
                    "upload/owner access to the given ownerId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned (possibly empty).",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @GetMapping("/metadata")
    public ResponseEntity<CommonResponseObject> list(
            @Parameter(description = "Logical document category to filter by.", required = true, example = "invoices")
            @RequestParam("category") @NotBlank String category,
            @Parameter(description = "Owner id to filter by.", required = true, example = "user-12345")
            @RequestParam("ownerId") @NotBlank String ownerId
    ) {
        return ResponseEntity.ok(CommonResponseObject.success(documentService.list(category, ownerId)));
    }

    @Operation(summary = "Search documents by category/owner (request-object form)",
            description = "Equivalent to the list endpoint, but accepts the category/ownerId filter wrapped in the common " +
                    "request-object envelope instead of query parameters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned (possibly empty).",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "requestObject is missing from the request envelope.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PostMapping("/metadata/search")
    public CommonResponseObject search(
            @Parameter(description = "Common request envelope whose requestObject is convertible to DocumentUploadRequest " +
                    "(only category/ownerId are read).", required = true)
            @RequestBody CommonRequestObject request) {
        DocumentUploadRequest filter = requestObject(request, DocumentUploadRequest.class);
        List<DocumentResponse> documents = documentService.list(filter.category(), filter.ownerId());
        return CommonResponseObject.success(documents);
    }

    @Operation(summary = "Delete a document",
            description = "Soft-deletes document metadata (after a retention/legal-hold check) and removes the underlying " +
                    "stored object, recording an audit event.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted."),
            @ApiResponse(responseCode = "400", description = "Document cannot be deleted (e.g. under legal hold or retention).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the document owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "Document metadata not found (or already deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @DeleteMapping("/metadata/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Document metadata id.", required = true) @PathVariable String id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Create a time-limited download URL",
            description = "Returns a pre-signed URL from the storage provider when supported, otherwise issues a signed " +
                    "app-level download token redeemable at the public download endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Download URL created.",
                    content = @Content(schema = @Schema(implementation = DocumentDownloadUrlResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the document owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "Document metadata not found (or already deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PostMapping("/metadata/{id}/download-url")
    public ResponseEntity<CommonResponseObject> createDownloadUrl(
            @Parameter(description = "Document metadata id.", required = true) @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(documentService.createDownloadUrl(id)));
    }

    @Operation(summary = "Set or clear legal hold on a document",
            description = "Toggles legal-hold status, which affects retention and delete eligibility, and records an audit event.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal hold updated.",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the document owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "Document metadata not found (or already deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PatchMapping("/metadata/{id}/legal-hold")
    public ResponseEntity<CommonResponseObject> updateLegalHold(
            @Parameter(description = "Document metadata id.", required = true) @PathVariable String id,
            @RequestBody LegalHoldRequest request
    ) {
        return ResponseEntity.ok(CommonResponseObject.success(documentService.updateLegalHold(id, request.enabled())));
    }

    @Operation(summary = "Confirm or correct the last day a document is valid",
            description = "A reviewer records the valid-until date read from the document (D12). A changed date restarts the expiry "
                    + "reminders; a confirmed date that is today or later lifts a driver's block for that document. Audited.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Date recorded.",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "No date given.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "403", description = "The caller owns the document (a driver cannot confirm their own date).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "No such document in the caller's organization (or deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @PatchMapping("/metadata/{id}/valid-until")
    public ResponseEntity<CommonResponseObject> confirmValidUntil(
            @Parameter(description = "Document metadata id.", required = true) @PathVariable String id,
            @jakarta.validation.Valid @RequestBody com.heuristiq.documents.document.dto.ValidUntilRequest request
    ) {
        return ResponseEntity.ok(CommonResponseObject.success(documentService.confirmValidUntil(id, request.validUntil())));
    }

    @Operation(summary = "Download a document via a signed public token",
            description = "Verifies the signed download token (issued by the download-url endpoint) and streams the " +
                    "underlying stored object, bypassing owner/role access checks since the token itself is the credential. " +
                    "Records an audit event.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document bytes streamed.",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
            @ApiResponse(responseCode = "400", description = "Token is invalid, expired, or malformed.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "Document metadata not found (or already deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @GetMapping("/public/{token}")
    public ResponseEntity<Resource> downloadByToken(
            @Parameter(description = "Signed, time-limited download token.", required = true) @PathVariable String token) {
        Resource resource = documentService.loadByDownloadToken(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .body(resource);
    }

    @Operation(summary = "Download a document by its storage object key",
            description = "Looks up metadata by the storage object key, enforces owner/privileged-role access, streams " +
                    "the underlying stored object, and records an audit event.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document bytes streamed.",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
            @ApiResponse(responseCode = "403", description = "Caller is not the document owner and does not hold a privileged role.",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class))),
            @ApiResponse(responseCode = "404", description = "Document metadata not found (or already deleted).",
                    content = @Content(schema = @Schema(implementation = CommonResponseObject.class)))
    })
    @GetMapping("/{*path}")
    public ResponseEntity<Resource> download(
            @Parameter(description = "Storage object key (category/ownerId/storedFileName), captured as the remainder of the path.",
                    required = true)
            @PathVariable String path) {
        String objectKey = path == null ? "" : path.replaceFirst("^/", "");
        Resource resource = documentService.loadByObjectKey(objectKey);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(resource);
    }

    private <T> T requestObject(CommonRequestObject request, Class<T> targetType) {
        if (request == null || request.getRequestObject() == null) {
            throw DmsException.badRequest("requestObject is required");
        }
        return objectMapper.convertValue(request.getRequestObject(), targetType);
    }
}
