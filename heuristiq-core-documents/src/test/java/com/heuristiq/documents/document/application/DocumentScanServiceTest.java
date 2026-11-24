package com.heuristiq.documents.document.application;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.scan.DocumentContentSniffer;
import com.heuristiq.documents.scan.ScanProviderRegistry;
import com.heuristiq.documents.scan.basic.BasicScanProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentScanServiceTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentContentSniffer contentSniffer = new DocumentContentSniffer();
    private final ScanProviderRegistry scanProviderRegistry =
            new ScanProviderRegistry(properties, List.of(new BasicScanProvider()));
    private final DocumentScanService service = new DocumentScanService(properties, contentSniffer, scanProviderRegistry);

    @Test
    void rejectsBlockedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.exe",
                "application/pdf",
                "content".getBytes()
        );

        assertThrows(DmsException.class, () -> service.scan(file, null));
    }

    @Test
    void rejectsEicarSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!".getBytes()
        );

        assertThrows(DmsException.class, () -> service.scan(file, null));
    }

    @Test
    void returnsSkippedWhenDisabled() {
        properties.getScan().setEnabled(false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.exe",
                "application/x-msdownload",
                "content".getBytes()
        );

        DocumentScanService.ScanResult result = service.scan(file, null);

        assertEquals("SKIPPED", result.status());
    }

    @Test
    void skipsProviderDelegationForPasswordProtectedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "statement.pdf",
                "application/pdf",
                "hello world".getBytes()
        );

        DocumentScanService.ScanResult result = service.scan(file, "s3cr3t");

        assertEquals("SKIPPED_PASSWORD_PROTECTED", result.status());
    }

    @Test
    void rejectsSniffedExecutableContentEvenWithAllowedExtension() {
        byte[] peHeader = new byte[64];
        peHeader[0] = 'M';
        peHeader[1] = 'Z';
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                peHeader
        );

        assertThrows(DmsException.class, () -> service.scan(file, null));
    }
}
