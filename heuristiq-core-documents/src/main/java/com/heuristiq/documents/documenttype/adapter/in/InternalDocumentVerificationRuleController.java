package com.railcab.dms.documenttype.adapter.in;

import com.railcab.dms.common.dto.CommonResponseObject;
import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-credential-only read of the same organization rule used by uploads. */
@RestController
@RequestMapping("/api/v1/dms/internal/document-types")
public class InternalDocumentVerificationRuleController {
    private final DocumentTypeQueryService types;

    public InternalDocumentVerificationRuleController(DocumentTypeQueryService types) { this.types = types; }

    @GetMapping("/verification-rule")
    public CommonResponseObject rule(@RequestHeader("X-Tenant-Id") String tenantId,
                                     @RequestParam("category") String category,
                                     @RequestParam("documentType") String documentType) {
        return CommonResponseObject.success(types.resolveRule(tenantId, category, documentType));
    }
}
