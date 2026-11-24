package com.railcab.dms.storage.local;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageProviderTest {

    @TempDir
    Path dir;
    LocalStorageProvider provider;

    @BeforeEach
    void setUp() {
        DmsProperties props = new DmsProperties();
        props.getStorage().setLocalRoot(dir.toString());
        provider = new LocalStorageProvider(props);
    }

    @Test
    void supportsLocalAndOnprem() {
        assertTrue(provider.supports("LOCAL"));
        assertTrue(provider.supports("onprem"));
        assertFalse(provider.supports("s3"));
    }

    @Test
    void storeLoadDeleteRoundTrip() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        StorageProvider.StoredObject stored = provider.store("t1/a.txt", file);
        assertEquals("t1/a.txt", stored.objectKey());
        assertEquals(5, stored.sizeBytes());
        assertNotNull(stored.checksumSha256());
        Resource r = provider.load("t1/a.txt");
        try (InputStream in = r.getInputStream()) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        provider.delete("t1/a.txt");
        assertFalse(Files.exists(dir.resolve("t1/a.txt")));
        provider.delete("t1/a.txt");
    }

    @Test
    void loadMissingIsNotFound() {
        DmsException ex = assertThrows(DmsException.class, () -> provider.load("nope.txt"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void pathTraversalIsForbidden() {
        DmsException ex = assertThrows(DmsException.class, () -> provider.load("../outside.txt"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertThrows(DmsException.class, () -> provider.delete("../../x"));
    }

    @Test
    void storeFailureWhenInputUnreadableIsBadRequest() {
        MockMultipartFile bad = new MockMultipartFile("f", "a.txt", "text/plain", new byte[0]) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("disk");
            }
        };
        DmsException ex = assertThrows(DmsException.class, () -> provider.store("k.txt", bad));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void healthyWhenRootWritable() {
        assertTrue(provider.isHealthy());
    }

    @Test
    void unhealthyWhenRootIsAFile() throws Exception {
        Path file = Files.createFile(dir.resolve("afile"));
        DmsProperties props = new DmsProperties();
        props.getStorage().setLocalRoot(file.toString());
        assertFalse(new LocalStorageProvider(props).isHealthy());
    }
}
