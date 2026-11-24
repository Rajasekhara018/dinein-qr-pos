package com.heuristq.dinein.image;

import com.heuristq.dinein.image.domain.ImageEntity;
import com.heuristq.dinein.image.domain.ImageRepository;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.SecureTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ImageService {

    private final ImageRepository imageRepository;
    private final ImageProcessor imageProcessor;
    private final AppProperties.Images props;
    private final Clock clock;

    public ImageService(ImageRepository imageRepository, ImageProcessor imageProcessor, AppProperties properties,
                        Clock clock) {
        this.imageRepository = imageRepository;
        this.imageProcessor = imageProcessor;
        this.props = properties.images();
        this.clock = clock;
    }

    public record UploadResult(Long imageId, String url, String thumbUrl, int width, int height) {
    }

    /**
     * Processes and stores an upload. Identical processed output is deduplicated by SHA-256: the content (and so the
     * cache key) is the same, so returning the existing id is safe. A different picture always gets a new id.
     */
    @Transactional
    public UploadResult upload(byte[] bytes) {
        ImageProcessor.Processed processed = imageProcessor.process(bytes);
        String sha = SecureTokens.sha256Hex(processed.main());
        List<ImageRepository.ImageMeta> existing = imageRepository.findMetaBySha256(sha);
        if (!existing.isEmpty()) {
            ImageRepository.ImageMeta meta = existing.getFirst();
            log.info("image.upload.deduplicated imageId={}", meta.getId());
            return new UploadResult(meta.getId(), ImageUrls.full(meta.getId()), ImageUrls.thumb(meta.getId()),
                    meta.getWidth() == null ? 0 : meta.getWidth(), meta.getHeight() == null ? 0 : meta.getHeight());
        }
        ImageEntity entity = new ImageEntity();
        entity.setContentType(processed.contentType());
        entity.setData(processed.main());
        entity.setThumbnail(processed.thumbnail());
        entity.setSizeBytes(processed.main().length);
        entity.setWidth(processed.width());
        entity.setHeight(processed.height());
        entity.setSha256(sha);
        imageRepository.save(entity);
        log.info("image.uploaded imageId={} bytes={} {}x{}", entity.getId(), entity.getSizeBytes(),
                processed.width(), processed.height());
        return new UploadResult(entity.getId(), ImageUrls.full(entity.getId()), ImageUrls.thumb(entity.getId()),
                processed.width(), processed.height());
    }

    @Transactional(readOnly = true)
    public void requireExists(Long imageId) {
        if (imageId != null && !imageRepository.existsById(imageId)) {
            throw ApiException.badRequest("IMAGE_NOT_FOUND", "Uploaded image not found. Please upload it again.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> findSha256(Long imageId) {
        return imageRepository.findMetaById(imageId).map(ImageRepository.ImageMeta::getSha256);
    }

    @Transactional(readOnly = true)
    public Optional<ImageRepository.ImageBytes> findBytes(Long imageId, boolean thumbnail) {
        return thumbnail ? imageRepository.findThumbBytes(imageId) : imageRepository.findMainBytes(imageId);
    }

    @Scheduled(cron = "${app.images.orphan-cleanup-cron}", zone = "Asia/Kolkata")
    @Transactional
    public void deleteOrphans() {
        int deleted = imageRepository.deleteOrphansCreatedBefore(clock.instant().minus(props.orphanGrace()));
        log.info("image.orphans.deleted count={}", deleted);
    }
}
