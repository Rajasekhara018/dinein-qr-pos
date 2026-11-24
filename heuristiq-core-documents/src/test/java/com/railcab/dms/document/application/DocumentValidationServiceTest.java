package com.railcab.dms.document.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentValidationServiceTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentValidationService service = new DocumentValidationService(properties);

    @Test
    void acceptsFileWithinConfiguredRules() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "pdf-bytes".getBytes()
        );

        assertDoesNotThrow(() -> service.validate(file, "kyc", null));
    }

    @Test
    void rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.exe",
                "application/pdf",
                "content".getBytes()
        );

        assertThrows(DmsException.class, () -> service.validate(file, "kyc", null));
    }

    @Test
    void rejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/x-msdownload",
                "content".getBytes()
        );

        assertThrows(DmsException.class, () -> service.validate(file, "kyc", null));
    }

    @Test
    void rejectsFileExceedingConfiguredMaxSize() {
        properties.getValidation().setMaxFileSizeBytes(4L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "way too big".getBytes()
        );

        assertThrows(DmsException.class, () -> service.validate(file, "kyc", null));
    }

    @Test
    void acceptsMatchingChecksum() throws Exception {
        byte[] content = "pdf-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", content);

        assertDoesNotThrow(() -> service.validate(file, "kyc", sha256Hex(content)));
    }

    @Test
    void rejectsMismatchedChecksum() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "pdf-bytes".getBytes()
        );

        assertThrows(DmsException.class, () -> service.validate(file, "kyc", "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    private static String sha256Hex(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
