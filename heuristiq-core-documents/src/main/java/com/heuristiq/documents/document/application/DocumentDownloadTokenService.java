package com.railcab.dms.document.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class DocumentDownloadTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";

    private final DmsProperties properties;

    public DocumentDownloadTokenService(DmsProperties properties) {
        this.properties = properties;
    }

    public String issueToken(String documentId, String objectKey) {
        long expiresAt = Instant.now().getEpochSecond() + properties.getSecurity().getDownloadTokenTtlSeconds();
        String payload = encode(documentId + "|" + objectKey + "|" + expiresAt);
        return payload + SEPARATOR + sign(payload);
    }

    public TokenClaims verify(String token) {
        if (!StringUtils.hasText(token) || !token.contains(SEPARATOR)) {
            throw DmsException.unauthorized("Invalid document download token");
        }
        String[] parts = token.split("\\.", 2);
        String expected = sign(parts[0]);
        if (!constantTimeEquals(expected, parts[1])) {
            throw DmsException.unauthorized("Invalid document download token");
        }
        String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] claims = decoded.split("\\|", 3);
        if (claims.length != 3) {
            throw DmsException.unauthorized("Invalid document download token");
        }
        long expiresAt = Long.parseLong(claims[2]);
        if (expiresAt < Instant.now().getEpochSecond()) {
            throw DmsException.unauthorized("Document download token has expired");
        }
        return new TokenClaims(claims[0], claims[1], expiresAt);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return encode(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to sign document download token");
        }
    }

    private String secret() {
        String configured = properties.getSecurity().getDownloadTokenSecret();
        if (!StringUtils.hasText(configured)) {
            throw DmsException.badRequest("DMS download token secret is not configured");
        }
        return configured;
    }

    private static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static boolean constantTimeEquals(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    public record TokenClaims(String documentId, String objectKey, long expiresAt) {
    }
}
