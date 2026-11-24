package com.railcab.dms.document.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.scan.DocumentContentSniffer;
import com.railcab.dms.scan.ScanProvider;
import com.railcab.dms.scan.ScanProviderRegistry;
import com.railcab.dms.scan.basic.BasicScanProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Mandatory ({@code scanRequired}) scans fail closed; optional scans keep their original behaviour. */
class DocumentScanServiceRequiredTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentContentSniffer sniffer = new DocumentContentSniffer();
    private final MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "hello".getBytes());

    private DocumentScanService serviceWith(ScanProvider provider) {
        properties.getScan().setProvider("stub");
        return new DocumentScanService(properties, sniffer, new ScanProviderRegistry(properties, List.of(provider)));
    }

    private static ScanProvider stub(ScanProvider.ScanVerdict verdict) {
        return new ScanProvider() {
            @Override
            public boolean supports(String provider) {
                return "stub".equals(provider);
            }

            @Override
            public ScanVerdict scan(MultipartFile f) {
                return verdict;
            }
        };
    }

    private static ScanProvider throwing() {
        return new ScanProvider() {
            @Override
            public boolean supports(String provider) {
                return "stub".equals(provider);
            }

            @Override
            public ScanVerdict scan(MultipartFile f) {
                throw new IllegalStateException("provider exploded");
            }
        };
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        assertEquals(expected, assertThrows(DmsException.class, action::run).getStatus());
    }

    @Test
    void requiredScanWithScanningDisabledIs503AndOptionalIsSkipped() {
        properties.getScan().setEnabled(false);
        DocumentScanService service = serviceWith(stub(new ScanProvider.ScanVerdict(true, "x")));
        properties.getScan().setEnabled(false);

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.scan(file, null, true));
        assertEquals("SKIPPED", service.scan(file, null, false).status());
        assertEquals("SKIPPED", service.scan(file, null).status());
    }

    @Test
    void requiredScanWithCleanVerdictPasses() {
        DocumentScanService service = serviceWith(stub(new ScanProvider.ScanVerdict(true, "all good")));

        assertEquals("CLEAN", service.scan(file, null, true).status());
    }

    @Test
    void requiredScanWithInfectedVerdictIs400() {
        DocumentScanService service = serviceWith(stub(new ScanProvider.ScanVerdict(false, "virus")));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.scan(file, null, true));
    }

    @Test
    void requiredScanWithInconclusiveFailOpenVerdictIs503ButOptionalStillPasses() {
        DocumentScanService service = serviceWith(stub(new ScanProvider.ScanVerdict(true, "allowed by fail-open", true)));

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.scan(file, null, true));
        assertEquals("CLEAN", service.scan(file, null, false).status());
    }

    @Test
    void requiredScanWithInconclusiveFailClosedVerdictIs503ButOptionalIs400() {
        DocumentScanService service = serviceWith(stub(new ScanProvider.ScanVerdict(false, "timed out", true)));

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.scan(file, null, true));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.scan(file, null, false));
    }

    @Test
    void requiredScanWhenTheProviderThrowsOrIsMissingIs503() {
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> serviceWith(throwing()).scan(file, null, true));

        DocumentScanService unsupported = serviceWith(stub(new ScanProvider.ScanVerdict(true, "x")));
        properties.getScan().setProvider("does-not-exist");
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> unsupported.scan(file, null, true));
    }

    @Test
    void passwordProtectedFileCannotSatisfyARequiredScan() {
        DocumentScanService service = serviceWith(stub(new ScanProvider.ScanVerdict(true, "x")));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.scan(file, "secret", true));
        assertEquals("SKIPPED_PASSWORD_PROTECTED", service.scan(file, "secret", false).status());
    }

    @Test
    void staticViolationsStillRejectedFirst() {
        DocumentScanService service = serviceWith(stub(new ScanProvider.ScanVerdict(true, "x")));
        MockMultipartFile exe = new MockMultipartFile("file", "a.exe", "application/pdf", "x".getBytes());

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.scan(exe, null, true));
    }

    @Test
    void basicProviderIoFailureIsInconclusive() {
        MultipartFile unreadable = new MockMultipartFile("file", "id.pdf", "application/pdf", new byte[0]) {
            @Override
            public byte[] getBytes() throws java.io.IOException {
                throw new java.io.IOException("disk");
            }
        };
        ScanProvider.ScanVerdict verdict = new BasicScanProvider().scan(unreadable);

        assertEquals(true, verdict.inconclusive());
        assertEquals(false, verdict.clean());
    }
}
