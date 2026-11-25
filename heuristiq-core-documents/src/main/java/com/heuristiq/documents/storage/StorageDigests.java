package com.heuristiq.documents.storage;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.exception.DmsException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class StorageDigests {

    private StorageDigests() {
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(DmsCommonConstants.SHA_256);
        } catch (NoSuchAlgorithmException ex) {
            throw DmsException.badRequest("SHA-256 digest is not available");
        }
    }

    public static String hex(MessageDigest digest) {
        byte[] bytes = digest.digest();
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
