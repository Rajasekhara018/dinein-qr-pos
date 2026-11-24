package com.railcab.dms.scan;

import org.springframework.web.multipart.MultipartFile;

public interface ScanProvider {

    boolean supports(String provider);

    ScanVerdict scan(MultipartFile file);

    /**
     * @param clean        whether the file may be accepted
     * @param message      human-readable detail
     * @param inconclusive true when the provider could not actually scan (unavailable, timed out, unreadable) and
     *                     {@code clean} only reflects a fail-open/fail-closed policy rather than a real verdict
     */
    record ScanVerdict(boolean clean, String message, boolean inconclusive) {

        public ScanVerdict(boolean clean, String message) {
            this(clean, message, false);
        }
    }
}
