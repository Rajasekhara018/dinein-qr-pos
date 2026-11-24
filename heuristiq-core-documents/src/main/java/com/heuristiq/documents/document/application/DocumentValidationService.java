package com.railcab.dms.document.application;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.document.audit.DocumentReasonCodes;
import com.railcab.dms.documenttype.application.DocumentTypeRules;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeRule;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.security.DmsSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Validates an upload against the rule that applies to it: the category rule of {@code railcab.dms.validation.*}
 * (exactly as before) or, when the upload names a {@code documentType}, the rule of that type of the caller's
 * organization.
 */
@Service
public class DocumentValidationService {

    private final DmsProperties properties;
    private final DocumentTypeRules documentTypeRules;

    public DocumentValidationService(DmsProperties properties) {
        this(properties, null);
    }

    @Autowired
    public DocumentValidationService(DmsProperties properties, DocumentTypeRules documentTypeRules) {
        this.properties = properties;
        this.documentTypeRules = documentTypeRules;
    }

    /** Validates against the category rule (no document type). */
    public void validate(MultipartFile file, String category, String expectedChecksum) {
        validate(file, category, expectedChecksum, (String) null);
    }

    /**
     * Validates an upload.
     *
     * @param documentType optional document type code of the caller's organization; when blank the category rule
     *                     applies and nothing else changes
     * @return the document type's rule when one was named (carries its retention and scan settings), else empty
     * @throws DmsException 400 for the first rule the file breaks, or for an unknown/inactive document type
     */
    public Optional<DocumentTypeRule> validate(MultipartFile file, String category, String expectedChecksum,
                                               String documentType) {
        if (file == null || file.isEmpty()) {
            throw DmsException.badRequest("Document file is required", DocumentReasonCodes.FILE_REQUIRED);
        }

        Optional<DocumentTypeRule> typeRule = resolveTypeRule(category, documentType);
        DmsProperties.ValidationRule rule = typeRule.map(DocumentValidationService::toRule)
                .orElseGet(() -> properties.validationFor(category));
        List<Violation> violations = inspect(file, rule);
        if (!violations.isEmpty()) {
            throw DmsException.badRequest(violations.get(0).message(), violations.get(0).code());
        }

        if (StringUtils.hasText(expectedChecksum)) {
            verifyChecksum(file, expectedChecksum);
        }
        return typeRule;
    }

    /**
     * The rule an upload must satisfy: the category rule, or the named document type's rule.
     *
     * @throws DmsException 400 when a document type is named but is unknown, inactive, of another category, or the
     *                      session has no organization
     */
    public DmsProperties.ValidationRule ruleFor(String category, String documentType) {
        return resolveTypeRule(category, documentType).map(DocumentValidationService::toRule)
                .orElseGet(() -> properties.validationFor(category));
    }

    /** The named document type's full rule, or empty when no type is named. Same failures as {@link #ruleFor}. */
    public Optional<DocumentTypeRule> resolveTypeRule(String category, String documentType) {
        if (!StringUtils.hasText(documentType)) {
            return Optional.empty();
        }
        if (documentTypeRules == null) {
            throw DmsException.badRequest("Document types are not available", DocumentReasonCodes.DOCUMENT_TYPES_UNAVAILABLE);
        }
        try {
            return Optional.of(documentTypeRules.resolveRule(
                    DmsSecurityContext.currentPrincipal().tenantId(), category, documentType));
        } catch (DocumentTypeException ex) {
            throw DmsException.badRequest(ex.getMessage(), DocumentReasonCodes.DOCUMENT_TYPE_INVALID);
        }
    }

    /** Converts a document type's rule to the shape the category rules use. */
    public static DmsProperties.ValidationRule toRule(DocumentTypeRule typeRule) {
        DmsProperties.ValidationRule rule = new DmsProperties.ValidationRule();
        rule.setMaxFileSizeBytes(typeRule.maxFileSizeBytes());
        rule.setAllowedExtensions(typeRule.allowedExtensions());
        rule.setAllowedContentTypes(typeRule.allowedContentTypes());
        return rule;
    }

    /**
     * Every rule {@code file} breaks (size, content type, extension), in that order; empty when it passes. Used by
     * uploads (first violation is thrown) and by the document-type "check a file" endpoint (all are reported).
     */
    public List<Violation> inspect(MultipartFile file, DmsProperties.ValidationRule rule) {
        List<Violation> violations = new ArrayList<>();
        if (rule.getMaxFileSizeBytes() != null && file.getSize() > rule.getMaxFileSizeBytes()) {
            violations.add(new Violation(DocumentReasonCodes.FILE_TOO_LARGE, "Document exceeds the configured maximum file size"));
        }

        String contentType = file.getContentType();
        boolean allowedContentType = rule.getAllowedContentTypes().stream()
                .anyMatch(type -> type.equalsIgnoreCase(contentType));
        if (!allowedContentType) {
            violations.add(new Violation(DocumentReasonCodes.CONTENT_TYPE_NOT_ALLOWED, "Document content type is not allowed"));
        }

        String originalName = file.getOriginalFilename();
        String lowerName = StringUtils.hasText(originalName)
                ? originalName.toLowerCase(Locale.ROOT)
                : "";
        boolean allowedExtension = rule.getAllowedExtensions().stream()
                .anyMatch(ext -> lowerName.endsWith(ext.toLowerCase(Locale.ROOT)));
        if (!allowedExtension) {
            violations.add(new Violation(DocumentReasonCodes.EXTENSION_NOT_ALLOWED, "Document file extension is not allowed"));
        }
        return violations;
    }

    /** One broken rule: a stable code and a human message. */
    public record Violation(String code, String message) {
    }

    private void verifyChecksum(MultipartFile file, String expectedChecksum) {
        String actual = sha256Hex(file);
        String normalizedExpected = expectedChecksum.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (!actual.equalsIgnoreCase(normalizedExpected)) {
            throw DmsException.badRequest("Document checksum does not match the expected value", DocumentReasonCodes.CHECKSUM_MISMATCH);
        }
    }

    private String sha256Hex(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DmsCommonConstants.SHA_256);
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw DmsException.badRequest("Unable to compute document checksum");
        }
    }
}
