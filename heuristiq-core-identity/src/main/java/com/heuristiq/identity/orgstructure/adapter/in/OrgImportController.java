package com.heuristiq.uam.orgstructure.adapter.in;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.orgstructure.adapter.in.OrgImportResponses.ImportErrorRow;
import com.heuristiq.uam.orgstructure.adapter.in.OrgStructureRequests.ImportRequest;
import com.heuristiq.uam.orgstructure.application.OrgImportUseCase;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Bulk employee import. The CSV is uploaded to the document store first; this API is given that document's id,
 * validates and stages its rows without changing the live organization, and applies only a confirmed import,
 * in the background.
 */
@RestController
@RequestMapping(OrgStructureWebConstants.BASE + "/imports")
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@Tag(name = "Organization Import", description = "Start from an uploaded CSV of employees, review the validation report, then confirm to apply it.")
public class OrgImportController {
    private final OrgImportUseCase useCase;
    private final CommonRequestMapper requestMapper;
    private final CallerContext caller;

    public OrgImportController(OrgImportUseCase useCase, CommonRequestMapper requestMapper, CallerContext caller) {
        this.useCase = useCase;
        this.requestMapper = requestMapper;
        this.caller = caller;
    }

    @Operation(summary = "Start an import from an uploaded file",
            description = "Payload is an ImportRequest with the document store id of a CSV with columns employee_code, full_name, "
                    + "department_code, position_name (required) and email, phone, manager_employee_code, joined_on (optional). "
                    + "Validates every row; changes nothing live.")
    @RequestObjectType(ImportRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(Authentication auth, @Valid @RequestBody CommonRequestObject body) {
        ImportRequest request = requestMapper.requireRequestObject(body, ImportRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                useCase.upload(caller.tenant(), CallerContext.actor(auth), request.documentId())));
    }

    @Operation(summary = "List recent imports")
    @GetMapping
    public CommonResponseObject recent(@RequestParam(defaultValue = "" + OrgStructureConstants.DEFAULT_PAGE_SIZE) int limit) {
        return CommonResponseObject.success(useCase.recent(caller.tenant(), limit));
    }

    @Operation(summary = "Get an import", description = "Status and counts; poll this while an import is applying.")
    @GetMapping("/{importId}")
    public CommonResponseObject get(@PathVariable String importId) {
        return CommonResponseObject.success(useCase.batch(caller.tenant(), importId));
    }

    @Operation(summary = "List the rejected rows of an import", description = "Keyset-paginated by row number.")
    @GetMapping("/{importId}/errors")
    public CommonResponseObject errors(@PathVariable String importId, @RequestParam(defaultValue = "0") int afterRow,
                                       @RequestParam(defaultValue = "" + OrgStructureConstants.DEFAULT_PAGE_SIZE) int limit) {
        return CommonResponseObject.success(useCase.errors(caller.tenant(), importId, afterRow, limit).stream()
                .map(ImportErrorRow::of).toList());
    }

    @Operation(summary = "Confirm an import", description = "Applies the valid rows in the background; rejected rows are skipped.")
    @PostMapping("/{importId}/confirm")
    public CommonResponseObject confirm(@PathVariable String importId) {
        return CommonResponseObject.success(useCase.confirm(caller.tenant(), importId));
    }

    @Operation(summary = "Cancel a validated import")
    @PostMapping("/{importId}/cancel")
    public CommonResponseObject cancel(@PathVariable String importId) {
        useCase.cancel(caller.tenant(), importId);
        return CommonResponseObject.success("Import cancelled", null);
    }
}
