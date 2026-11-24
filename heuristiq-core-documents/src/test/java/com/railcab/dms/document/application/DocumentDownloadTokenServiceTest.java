package com.railcab.dms.document.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentDownloadTokenServiceTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentDownloadTokenService service = new DocumentDownloadTokenService(properties);

    @Test
    void verifiesIssuedToken() {
        String token = service.issueToken("doc-1", "owner-1/file.pdf");

        DocumentDownloadTokenService.TokenClaims claims = service.verify(token);

        assertEquals("doc-1", claims.documentId());
        assertEquals("owner-1/file.pdf", claims.objectKey());
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.issueToken("doc-1", "owner-1/file.pdf");

        assertThrows(DmsException.class, () -> service.verify(token + "x"));
    }

    @Test
    void rejectsExpiredToken() {
        properties.getSecurity().setDownloadTokenTtlSeconds(-1);
        String token = service.issueToken("doc-1", "owner-1/file.pdf");

        assertThrows(DmsException.class, () -> service.verify(token));
    }
}
