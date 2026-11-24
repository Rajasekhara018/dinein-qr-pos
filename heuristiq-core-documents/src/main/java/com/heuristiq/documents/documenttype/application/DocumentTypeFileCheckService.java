package com.railcab.dms.documenttype.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.document.application.DocumentScanService;
import com.railcab.dms.document.application.DocumentValidationService;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeRule;
import com.railcab.dms.exception.DmsException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * "Would this file be accepted?" for one document type of the caller's organization. Runs the same checks as an upload
 * (size, content type, extension from the type's rule; blocked types and the magic-byte sniff from the scan rules) but
 * stores nothing and calls no antivirus provider. All problems are reported, not just the first.
 *
 * <p>The file arrives as Spring's {@code MultipartFile} because the existing DMS validation, scan and content-sniff
 * components all take that type; there is no second file abstraction in this service.
 */
@Service
public class DocumentTypeFileCheckService {

    private final DocumentTypeRules rules;
    private final DocumentValidationService validationService;
    private final DocumentScanService scanService;

    public DocumentTypeFileCheckService(DocumentTypeRules rules, DocumentValidationService validationService,
                                        DocumentScanService scanService) {
        this.rules = rules;
        this.validationService = validationService;
        this.scanService = scanService;
    }

    /**
     * Checks a file against a document type.
     *
     * @param tenantId         the caller's organization
     * @param category         optional; when given it must match the type's category
     * @param documentTypeCode the type's code
     * @param file             the sample file
     * @return the verdict with the rule that was applied
     * @throws com.railcab.dms.documenttype.domain.DocumentTypeException INVALID for an unknown or inactive type
     */
    public Result check(String tenantId, String category, String documentTypeCode, MultipartFile file) {
        DocumentTypeRule rule = rules.resolveRule(tenantId, category, documentTypeCode);
        List<Reason> reasons = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            reasons.add(new Reason(DocumentTypeConstants.REASON_EMPTY_FILE, "Document file is required"));
            return new Result(false, reasons, rule);
        }
        DmsProperties.ValidationRule dmsRule = DocumentValidationService.toRule(rule);
        for (DocumentValidationService.Violation violation : validationService.inspect(file, dmsRule)) {
            reasons.add(new Reason(violation.code(), violation.message()));
        }
        try {
            scanService.rejectStaticViolations(file);
        } catch (DmsException ex) {
            reasons.add(new Reason(DocumentTypeConstants.REASON_BLOCKED, ex.getMessage()));
        }
        return new Result(reasons.isEmpty(), reasons, rule);
    }

    /** One reason a file was refused. */
    public record Reason(String code, String message) {
    }

    /** The verdict. */
    public record Result(boolean accepted, List<Reason> reasons, DocumentTypeRule rule) {
    }
}
