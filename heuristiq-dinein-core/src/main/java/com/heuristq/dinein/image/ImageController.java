package com.heuristq.dinein.image;

import com.heuristq.dinein.image.domain.ImageRepository;
import com.heuristq.dinein.shared.exception.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Public image bytes. Image rows are immutable (a replaced image gets a new id), so responses are cached for a year.
 * The ETag check runs against the hash only, so a 304 never loads the BYTEA column.
 */
@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private static final CacheControl IMMUTABLE = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> main(@PathVariable Long id,
                                       @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return serve(id, false, ifNoneMatch);
    }

    @GetMapping("/{id}/thumb")
    public ResponseEntity<byte[]> thumb(@PathVariable Long id,
                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return serve(id, true, ifNoneMatch);
    }

    private ResponseEntity<byte[]> serve(Long id, boolean thumbnail, String ifNoneMatch) {
        String sha = imageService.findSha256(id).orElseThrow(() -> ApiException.notFound("Image"));
        String etag = "\"" + sha + (thumbnail ? "-t" : "") + "\"";
        if (ifNoneMatch != null && (ifNoneMatch.contains(etag) || ifNoneMatch.trim().equals("*"))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(IMMUTABLE).build();
        }
        ImageRepository.ImageBytes image = imageService.findBytes(id, thumbnail)
                .orElseThrow(() -> ApiException.notFound("Image"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .contentLength(image.getBytes().length)
                .eTag(etag)
                .cacheControl(IMMUTABLE)
                .header("X-Content-Type-Options", "nosniff")
                .body(image.getBytes());
    }
}
