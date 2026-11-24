package com.heuristiq.documents.scan;

import com.heuristiq.documents.exception.DmsException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Detects a file's actual MIME type from its bytes (magic numbers), independent of whatever
 * Content-Type/extension the client declared -- closes the gap where a renamed executable with
 * a spoofed extension/content-type would otherwise pass the declared-type allowlist checks.
 */
@Component
public class DocumentContentSniffer {

    private final Tika tika = new Tika();

    public String detect(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return tika.detect(in, file.getOriginalFilename());
        } catch (IOException ex) {
            throw DmsException.badRequest("Unable to inspect document content");
        }
    }
}
