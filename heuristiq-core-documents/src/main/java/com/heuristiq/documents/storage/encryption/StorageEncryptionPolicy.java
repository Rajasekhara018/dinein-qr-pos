package com.heuristiq.documents.storage.encryption;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure startup rules for the encryption settings: which combinations of provider capability, mode, key and
 * {@code require-encryption} are refused (errors) or merely have no effect (warnings). No framework types.
 */
public final class StorageEncryptionPolicy {

    private StorageEncryptionPolicy() {
    }

    /**
     * Evaluates the settings.
     *
     * @param providerName      configured provider name (for messages)
     * @param capability        what the provider can do
     * @param mode              requested mode (null is treated as NONE)
     * @param kmsKeyId          key id/name (may be blank)
     * @param requireEncryption whether startup must fail unless documents are encrypted at rest
     */
    public static Result evaluate(String providerName, EncryptionCapability capability, EncryptionMode mode,
                                  String kmsKeyId, boolean requireEncryption) {
        EncryptionMode effective = mode == null ? EncryptionMode.NONE : mode;
        boolean hasKey = kmsKeyId != null && !kmsKeyId.isBlank();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (effective == EncryptionMode.SSE_KMS && !hasKey) {
            errors.add("railcab.dms.storage.encryption.kms-key-id is required when mode is SSE_KMS");
        }
        if (effective != EncryptionMode.SSE_KMS && hasKey) {
            warnings.add("railcab.dms.storage.encryption.kms-key-id is ignored unless mode is SSE_KMS");
        }
        if (capability == EncryptionCapability.PLAINTEXT_ONLY && effective != EncryptionMode.NONE) {
            errors.add("Storage provider '" + providerName + "' stores documents as plaintext, so encryption mode "
                    + effective + " cannot be honoured. Use mode NONE or an encrypting provider (s3, minio, azure, gcp)");
        }
        if (capability == EncryptionCapability.PROVIDER_MANAGED && effective == EncryptionMode.SSE_KMS) {
            errors.add("Storage provider '" + providerName + "' cannot apply a customer-managed key per request; "
                    + "configure the key on the storage account/container and use mode NONE or SSE_S3");
        }
        if ((capability == EncryptionCapability.PROVIDER_MANAGED
                || capability == EncryptionCapability.PROVIDER_MANAGED_WITH_KMS) && effective == EncryptionMode.SSE_S3) {
            warnings.add("Storage provider '" + providerName + "' always encrypts with provider-managed keys; "
                    + "mode SSE_S3 has no additional effect");
        }
        if (requireEncryption && (capability == EncryptionCapability.PLAINTEXT_ONLY || effective == EncryptionMode.NONE)) {
            errors.add("railcab.dms.storage.require-encryption is true, but storage provider '" + providerName
                    + "' with encryption mode " + effective + " does not encrypt documents at rest. Use a non-local "
                    + "provider and set railcab.dms.storage.encryption.mode to SSE_S3 or SSE_KMS");
        }
        return new Result(List.copyOf(errors), List.copyOf(warnings));
    }

    /**
     * The value recorded on a stored document for what the provider actually did.
     *
     * @return the mode name for S3/MinIO, {@code SSE_KMS} for GCP with a key, {@code NONE} for plaintext,
     *         {@code PROVIDER_MANAGED} otherwise
     */
    public static String recordedEncryption(EncryptionCapability capability, EncryptionMode mode) {
        EncryptionMode effective = mode == null ? EncryptionMode.NONE : mode;
        return switch (capability) {
            case PLAINTEXT_ONLY -> EncryptionMode.NONE.name();
            case SERVER_SIDE -> effective.name();
            case PROVIDER_MANAGED_WITH_KMS -> effective == EncryptionMode.SSE_KMS
                    ? EncryptionMode.SSE_KMS.name() : StorageEncryptionConstants.PROVIDER_MANAGED;
            case PROVIDER_MANAGED -> StorageEncryptionConstants.PROVIDER_MANAGED;
        };
    }

    /** Outcome of {@link #evaluate}. */
    public record Result(List<String> errors, List<String> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
