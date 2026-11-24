package com.heuristiq.documents.storage.local;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.storage.StorageDigests;
import com.heuristiq.documents.storage.StorageProvider;
import com.heuristiq.documents.storage.encryption.EncryptionCapability;
import com.heuristiq.documents.storage.encryption.StorageEncryptionPolicy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalStorageProvider implements StorageProvider {

    private final Path root;

    public LocalStorageProvider(DmsProperties properties) {
        this.root = Paths.get(properties.getStorage().getLocalRoot()).toAbsolutePath().normalize();
    }

    @Override
    public boolean supports(String provider) {
        return "local".equalsIgnoreCase(provider) || "onprem".equalsIgnoreCase(provider);
    }

    @Override
    public StoredObject store(String objectKey, MultipartFile file) {
        Path target = resolve(objectKey);
        MessageDigest digest = StorageDigests.sha256();
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            Files.createDirectories(target.getParent());
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject(objectKey, null, file.getSize(), file.getContentType(), StorageDigests.hex(digest),
                    StorageEncryptionPolicy.recordedEncryption(EncryptionCapability.PLAINTEXT_ONLY, null));
        } catch (IOException ex) {
            throw DmsException.badRequest("Unable to store document on local storage");
        }
    }

    @Override
    public Resource load(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw DmsException.notFound("Document not found");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException ex) {
            throw DmsException.badRequest("Invalid document path");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ex) {
            throw DmsException.badRequest("Unable to delete document from local storage");
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            Files.createDirectories(root);
            return Files.isDirectory(root) && Files.isWritable(root);
        } catch (IOException ex) {
            return false;
        }
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey == null ? "" : objectKey).normalize();
        if (!target.startsWith(root)) {
            throw DmsException.forbidden("Invalid document path");
        }
        return target;
    }
}
