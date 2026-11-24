package com.railcab.dms.scan;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentContentSnifferTest {

    private final DocumentContentSniffer sniffer = new DocumentContentSniffer();

    @Test
    void detectsRealPdfContentRegardlessOfDeclaredType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "%PDF-1.4\n%some pdf body".getBytes()
        );

        assertEquals("application/pdf", sniffer.detect(file));
    }

    @Test
    void detectsExecutableContentDisguisedWithAnAllowedExtension() {
        byte[] peHeader = new byte[64];
        peHeader[0] = 'M';
        peHeader[1] = 'Z';
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                peHeader
        );

        String detected = sniffer.detect(file);

        assertTrue(detected.contains("msdownload") || detected.contains("dosexec") || detected.contains("executable"));
    }
}
