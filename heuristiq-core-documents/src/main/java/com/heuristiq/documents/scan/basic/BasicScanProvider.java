package com.heuristiq.documents.scan.basic;

import com.heuristiq.documents.scan.ScanConstants;
import com.heuristiq.documents.scan.ScanProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class BasicScanProvider implements ScanProvider {

    private static final String EICAR_SIGNATURE = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR";

    @Override
    public boolean supports(String provider) {
        return provider == null || provider.isBlank() || ScanConstants.BASIC_PROVIDER.equalsIgnoreCase(provider);
    }

    @Override
    public ScanVerdict scan(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String sample = new String(bytes, 0, Math.min(bytes.length, ScanConstants.BASIC_SAMPLE_BYTES), StandardCharsets.US_ASCII);
            if (sample.contains(EICAR_SIGNATURE)) {
                return new ScanVerdict(false, "EICAR test signature detected");
            }
            return new ScanVerdict(true, "Basic scanner passed");
        } catch (IOException ex) {
            return new ScanVerdict(false, "Unable to scan document", true);
        }
    }
}
