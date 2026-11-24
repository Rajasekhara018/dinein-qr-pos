package com.railcab.dms.document.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.document.audit.DocumentReasonCodes;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.scan.DocumentContentSniffer;
import com.railcab.dms.scan.ScanProvider;
import com.railcab.dms.scan.ScanProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
public class DocumentScanService {

    private static final Logger log = LoggerFactory.getLogger(DocumentScanService.class);

    private final DmsProperties properties;
    private final DocumentContentSniffer contentSniffer;
    private final ScanProviderRegistry scanProviderRegistry;

    public DocumentScanService(
            DmsProperties properties,
            DocumentContentSniffer contentSniffer,
            ScanProviderRegistry scanProviderRegistry
    ) {
        this.properties = properties;
        this.contentSniffer = contentSniffer;
        this.scanProviderRegistry = scanProviderRegistry;
    }

    /** Scans without the mandatory-scan guarantee (uploads that name no document type). */
    /** Name of the scan provider in use, or {@code none} when scanning is disabled. */
    public String currentProviderName() {
        return properties.getScan().isEnabled() ? properties.getScan().getProvider() : DocumentConstants.NO_PROVIDER;
    }

    public ScanResult scan(MultipartFile file, String password) {
        return scan(file, password, false);
    }

    /**
     * Scans an upload.
     *
     * <p>When {@code scanRequired} is true the scan fails closed: nothing may be accepted unless a provider gave a
     * real "clean" verdict. Scanning disabled, an unsupported/failing provider, an inconclusive verdict (unavailable,
     * timed out, or let through by a fail-open policy) all answer 503, and a password-protected file (which cannot be
     * scanned) answers 400. When false the behaviour is the original one (skip when disabled or password-protected,
     * honour the provider's fail-open policy).
     *
     * @throws DmsException 400 for a blocked/infected/unscannable-by-nature file; 503 when a required scan could not run
     */
    public ScanResult scan(MultipartFile file, String password, boolean scanRequired) {
        if (!properties.getScan().isEnabled()) {
            if (scanRequired) {
                throw scanUnavailable("malware scanning is disabled", DocumentReasonCodes.SCAN_UNAVAILABLE);
            }
            return new ScanResult(DocumentConstants.SCAN_SKIPPED, "Scanning disabled", DocumentConstants.NO_PROVIDER);
        }
        rejectStaticViolations(file);

        if (StringUtils.hasText(password)) {
            if (scanRequired) {
                throw DmsException.badRequest(
                        "This document type requires a malware scan, so password-protected files are not accepted",
                        DocumentReasonCodes.PASSWORD_PROTECTED_NOT_SCANNABLE);
            }
            return new ScanResult(DocumentConstants.SCAN_SKIPPED_PASSWORD_PROTECTED, "Malware scan skipped -- file is password protected", DocumentConstants.NO_PROVIDER);
        }

        ScanProvider.ScanVerdict verdict = scanRequired ? scanOrFailClosed(file) : scanProviderRegistry.current().scan(file);
        if (scanRequired && verdict.inconclusive()) {
            throw scanUnavailable("the scan provider could not give a verdict", DocumentReasonCodes.SCAN_INCONCLUSIVE);
        }
        if (!verdict.clean()) {
            throw DmsException.badRequest("Document failed malware scan: " + verdict.message(), DocumentReasonCodes.MALWARE_DETECTED);
        }
        return new ScanResult(DocumentConstants.SCAN_CLEAN, verdict.message(), properties.getScan().getProvider());
    }

    /** Runs the provider; any failure of the provider itself is a "no verdict" for a required scan. */
    private ScanProvider.ScanVerdict scanOrFailClosed(MultipartFile file) {
        try {
            return scanProviderRegistry.current().scan(file);
        } catch (RuntimeException ex) {
            log.error("dms.scan.required provider failed: {}", ex.getMessage(), ex);
            throw scanUnavailable("the scan provider failed", DocumentReasonCodes.SCAN_UNAVAILABLE);
        }
    }

    private static DmsException scanUnavailable(String reason, String reasonCode) {
        return DmsException.serviceUnavailable(
                "A malware scan is required for this document type but " + reason + ". Nothing was stored; try again later",
                reasonCode);
    }

    /**
     * The checks that need no antivirus provider: blocked extension, blocked content type and a magic-byte sniff for
     * dangerous types. Nothing is stored or sent anywhere. Does nothing when scanning is disabled, like {@link #scan}.
     *
     * @throws DmsException 400 for the first violation
     */
    public void rejectStaticViolations(MultipartFile file) {
        if (!properties.getScan().isEnabled()) {
            return;
        }
        rejectBlockedExtension(file.getOriginalFilename());
        rejectBlockedContentType(file.getContentType());
        rejectSniffedDangerousContentType(file);
    }

    private void rejectBlockedExtension(String originalName) {
        String lowerName = StringUtils.hasText(originalName)
                ? originalName.toLowerCase(Locale.ROOT)
                : "";
        boolean blocked = properties.getScan().getBlockedExtensions().stream()
                .filter(StringUtils::hasText)
                .anyMatch(ext -> lowerName.endsWith(ext.toLowerCase(Locale.ROOT)));
        if (blocked) {
            throw DmsException.badRequest("Document type is blocked by scan policy", DocumentReasonCodes.BLOCKED_EXTENSION);
        }
    }

    private void rejectBlockedContentType(String contentType) {
        boolean blocked = properties.getScan().getBlockedContentTypes().stream()
                .filter(StringUtils::hasText)
                .anyMatch(type -> type.equalsIgnoreCase(contentType));
        if (blocked) {
            throw DmsException.badRequest("Document content type is blocked by scan policy", DocumentReasonCodes.BLOCKED_CONTENT_TYPE);
        }
    }

    private void rejectSniffedDangerousContentType(MultipartFile file) {
        String sniffedType = contentSniffer.detect(file);
        boolean dangerous = properties.getScan().getDangerousContentTypes().stream()
                .filter(StringUtils::hasText)
                .anyMatch(type -> type.equalsIgnoreCase(sniffedType));
        if (dangerous) {
            throw DmsException.badRequest("Document content does not match a safe file type", DocumentReasonCodes.DANGEROUS_CONTENT);
        }
    }

    /** @param provider the scan provider that gave the verdict, or {@code none} when no scan ran */
    public record ScanResult(String status, String message, String provider) {

        public ScanResult(String status, String message) {
            this(status, message, DocumentConstants.NO_PROVIDER);
        }
    }
}
