package com.heuristiq.documents.document.application;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.storage.StorageProvider;
import com.heuristiq.documents.storage.StorageProviderRegistry;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Optional;

@Service
public class DocumentObjectService {

    private final DmsProperties properties;
    private final StorageProviderRegistry providerRegistry;

    public DocumentObjectService(DmsProperties properties, StorageProviderRegistry providerRegistry) {
        this.properties = properties;
        this.providerRegistry = providerRegistry;
    }

    public StoredDocument store(String objectKey, MultipartFile file) {
        StorageProvider.StoredObject stored = providerRegistry.current().store(objectKey, file);
        return new StoredDocument(
                stored.objectKey(),
                publicUrl(stored.objectKey()),
                providerRegistry.currentProviderName(),
                stored.versionId(),
                stored.checksumSha256(),
                stored.contentType(),
                stored.sizeBytes(),
                stored.encryption()
        );
    }

    public Resource load(String objectKey, String provider) {
        return resolveProvider(provider).load(objectKey);
    }

    public void delete(String objectKey, String provider) {
        resolveProvider(provider).delete(objectKey);
    }

    public Optional<StorageProvider.PresignedDownload> presignDownload(String objectKey, String provider) {
        Duration ttl = Duration.ofSeconds(properties.getSecurity().getDownloadTokenTtlSeconds());
        return resolveProvider(provider).presignDownload(objectKey, ttl);
    }

    private StorageProvider resolveProvider(String provider) {
        return StringUtils.hasText(provider) ? providerRegistry.forProvider(provider) : providerRegistry.current();
    }

    private String publicUrl(String objectKey) {
        return joinUrl(properties.getStorage().getPublicBaseUrl(), objectKey);
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll(DmsCommonConstants.TRAILING_SLASHES_REGEX, "");
        String suffix = path == null ? "" : path.replaceAll("^/+", "");
        return base + "/" + suffix;
    }

    public record StoredDocument(
            String objectKey,
            String publicUrl,
            String provider,
            String versionId,
            String checksumSha256,
            String contentType,
            long sizeBytes,
            String encryption
    ) {
    }
}
