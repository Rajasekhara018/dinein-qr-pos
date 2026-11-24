package com.railcab.dms.config;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.scan.ScanConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.railcab.dms.storage.encryption.EncryptionMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "railcab.dms")
public class DmsProperties {

    private Storage storage = new Storage();
    private Persistence persistence = new Persistence();
    private ValidationRule validation = new ValidationRule();
    private Map<String, ValidationRule> categoryValidation = new HashMap<>();
    private Security security = new Security();
    private Scan scan = new Scan();
    private Lifecycle lifecycle = new Lifecycle();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public void setPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

    public ValidationRule getValidation() {
        return validation;
    }

    public void setValidation(ValidationRule validation) {
        this.validation = validation;
    }

    public Map<String, ValidationRule> getCategoryValidation() {
        return categoryValidation;
    }

    public void setCategoryValidation(Map<String, ValidationRule> categoryValidation) {
        this.categoryValidation = categoryValidation;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Scan getScan() {
        return scan;
    }

    public void setScan(Scan scan) {
        this.scan = scan;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public ValidationRule validationFor(String category) {
        ValidationRule categoryRule = category == null ? null : categoryValidation.get(category.toLowerCase());
        return categoryRule == null ? validation : categoryRule.merge(validation);
    }

    public static class Storage {
        private String provider = "local";
        private String localRoot = "uploads/documents";
        private String publicBaseUrl = DmsCommonConstants.DOCUMENTS_BASE_PATH;
        private int operationTimeoutSeconds = 30;
        private int attemptTimeoutSeconds = 10;
        private AwsS3 awsS3 = new AwsS3();
        private Minio minio = new Minio();
        private AzureBlob azureBlob = new AzureBlob();
        private GcpStorage gcp = new GcpStorage();
        private Encryption encryption = new Encryption();
        private boolean requireEncryption = false;

        public Encryption getEncryption() {
            return encryption;
        }

        public void setEncryption(Encryption encryption) {
            this.encryption = encryption;
        }

        /** When true, startup fails unless documents are stored by a non-local provider with encryption mode not NONE. */
        public boolean isRequireEncryption() {
            return requireEncryption;
        }

        public void setRequireEncryption(boolean requireEncryption) {
            this.requireEncryption = requireEncryption;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getLocalRoot() {
            return localRoot;
        }

        public void setLocalRoot(String localRoot) {
            this.localRoot = localRoot;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public int getOperationTimeoutSeconds() {
            return operationTimeoutSeconds;
        }

        public void setOperationTimeoutSeconds(int operationTimeoutSeconds) {
            this.operationTimeoutSeconds = operationTimeoutSeconds;
        }

        public int getAttemptTimeoutSeconds() {
            return attemptTimeoutSeconds;
        }

        public void setAttemptTimeoutSeconds(int attemptTimeoutSeconds) {
            this.attemptTimeoutSeconds = attemptTimeoutSeconds;
        }

        public AwsS3 getAwsS3() {
            return awsS3;
        }

        public void setAwsS3(AwsS3 awsS3) {
            this.awsS3 = awsS3;
        }

        public Minio getMinio() {
            return minio;
        }

        public void setMinio(Minio minio) {
            this.minio = minio;
        }

        public AzureBlob getAzureBlob() {
            return azureBlob;
        }

        public void setAzureBlob(AzureBlob azureBlob) {
            this.azureBlob = azureBlob;
        }

        public GcpStorage getGcp() {
            return gcp;
        }

        public void setGcp(GcpStorage gcp) {
            this.gcp = gcp;
        }
    }

    /** Encryption at rest requested from the storage backend (see StorageEncryptionPolicy for the allowed combinations). */
    public static class Encryption {
        private EncryptionMode mode = EncryptionMode.NONE;
        private String kmsKeyId;

        public EncryptionMode getMode() {
            return mode;
        }

        public void setMode(EncryptionMode mode) {
            this.mode = mode == null ? EncryptionMode.NONE : mode;
        }

        /** Key id/ARN (S3, MinIO) or full key resource name (GCP); required when the mode is SSE_KMS. */
        public String getKmsKeyId() {
            return kmsKeyId;
        }

        public void setKmsKeyId(String kmsKeyId) {
            this.kmsKeyId = kmsKeyId;
        }
    }

    public static class Persistence {
        private String provider = "mongo";
        private Postgres postgres = new Postgres();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Postgres getPostgres() {
            return postgres;
        }

        public void setPostgres(Postgres postgres) {
            this.postgres = postgres;
        }

        public static class Postgres {
            private String jdbcUrl = "jdbc:postgresql://localhost:5432/railcab_dms";
            private String username = "railcab_dms";
            private String password;
            private int maximumPoolSize = 20;
            private int minimumIdle = 5;
            private long connectionTimeoutMs = 10000;
            private long idleTimeoutMs = 300000;
            private long maxLifetimeMs = 1500000;
            private long leakDetectionThresholdMs = 30000;

            public String getJdbcUrl() {
                return jdbcUrl;
            }

            public void setJdbcUrl(String jdbcUrl) {
                this.jdbcUrl = jdbcUrl;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String username) {
                this.username = username;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password;
            }

            public int getMaximumPoolSize() {
                return maximumPoolSize;
            }

            public void setMaximumPoolSize(int maximumPoolSize) {
                this.maximumPoolSize = maximumPoolSize;
            }

            public int getMinimumIdle() {
                return minimumIdle;
            }

            public void setMinimumIdle(int minimumIdle) {
                this.minimumIdle = minimumIdle;
            }

            public long getConnectionTimeoutMs() {
                return connectionTimeoutMs;
            }

            public void setConnectionTimeoutMs(long connectionTimeoutMs) {
                this.connectionTimeoutMs = connectionTimeoutMs;
            }

            public long getIdleTimeoutMs() {
                return idleTimeoutMs;
            }

            public void setIdleTimeoutMs(long idleTimeoutMs) {
                this.idleTimeoutMs = idleTimeoutMs;
            }

            public long getMaxLifetimeMs() {
                return maxLifetimeMs;
            }

            public void setMaxLifetimeMs(long maxLifetimeMs) {
                this.maxLifetimeMs = maxLifetimeMs;
            }

            public long getLeakDetectionThresholdMs() {
                return leakDetectionThresholdMs;
            }

            public void setLeakDetectionThresholdMs(long leakDetectionThresholdMs) {
                this.leakDetectionThresholdMs = leakDetectionThresholdMs;
            }
        }
    }

    public static class Security {
        private boolean enforceOwnerAccess = true;
        private String downloadTokenSecret = DmsSecurityDefaults.DEFAULT_DOWNLOAD_TOKEN_SECRET;
        private long downloadTokenTtlSeconds = 300;
        /**
         * Transitional escape hatch, EMPTY by default: role names whose holders may reach other people's documents
         * through the owner check. Access is decided by the owner, by a trusted internal service
         * ({@link com.railcab.dms.security.DmsPrincipal#isInternalService()}), and for staff by catalogue privileges
         * enforced at the gateway - not by role names. Set only to keep an old deployment working while its staff
         * flows move to privileges, then remove it.
         */
        @Deprecated
        private List<String> privilegedRoles = List.of();

        public boolean isEnforceOwnerAccess() {
            return enforceOwnerAccess;
        }

        public void setEnforceOwnerAccess(boolean enforceOwnerAccess) {
            this.enforceOwnerAccess = enforceOwnerAccess;
        }

        public String getDownloadTokenSecret() {
            return downloadTokenSecret;
        }

        public void setDownloadTokenSecret(String downloadTokenSecret) {
            this.downloadTokenSecret = downloadTokenSecret;
        }

        public long getDownloadTokenTtlSeconds() {
            return downloadTokenTtlSeconds;
        }

        public void setDownloadTokenTtlSeconds(long downloadTokenTtlSeconds) {
            this.downloadTokenTtlSeconds = downloadTokenTtlSeconds;
        }

        public List<String> getPrivilegedRoles() {
            return privilegedRoles;
        }

        public void setPrivilegedRoles(List<String> privilegedRoles) {
            this.privilegedRoles = privilegedRoles;
        }
    }

    public static class Scan {
        private boolean enabled = true;
        private String provider = ScanConstants.BASIC_PROVIDER;
        private List<String> blockedExtensions = List.of(".exe", ".dll", ".bat", ".cmd", ".js", ".sh", ".jar");
        private List<String> blockedContentTypes = List.of(
                "application/x-msdownload",
                "application/x-sh",
                "application/java-archive"
        );
        private List<String> dangerousContentTypes = List.of(
                "application/x-msdownload",
                "application/x-dosexec",
                "application/x-executable",
                "application/x-elf",
                "application/x-sharedlib",
                "application/x-mach-binary",
                "application/java-archive",
                "application/x-sh"
        );
        private Metadefender metadefender = new Metadefender();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public List<String> getBlockedExtensions() {
            return blockedExtensions;
        }

        public void setBlockedExtensions(List<String> blockedExtensions) {
            this.blockedExtensions = blockedExtensions;
        }

        public List<String> getBlockedContentTypes() {
            return blockedContentTypes;
        }

        public void setBlockedContentTypes(List<String> blockedContentTypes) {
            this.blockedContentTypes = blockedContentTypes;
        }

        public List<String> getDangerousContentTypes() {
            return dangerousContentTypes;
        }

        public void setDangerousContentTypes(List<String> dangerousContentTypes) {
            this.dangerousContentTypes = dangerousContentTypes;
        }

        public Metadefender getMetadefender() {
            return metadefender;
        }

        public void setMetadefender(Metadefender metadefender) {
            this.metadefender = metadefender;
        }
    }

    public static class Metadefender {
        private String apiKey;
        private String uploadUrl = "https://api.metadefender.com/v4/file";
        private String resultUrlBase = "https://api.metadefender.com/v4/file/";
        private int pollAttempts = 15;
        private long pollIntervalMillis = 2000L;
        private int connectTimeoutMs = 5000;
        private int responseTimeoutMs = 30000;
        private boolean failOpen = false;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getUploadUrl() {
            return uploadUrl;
        }

        public void setUploadUrl(String uploadUrl) {
            this.uploadUrl = uploadUrl;
        }

        public String getResultUrlBase() {
            return resultUrlBase;
        }

        public void setResultUrlBase(String resultUrlBase) {
            this.resultUrlBase = resultUrlBase;
        }

        public int getPollAttempts() {
            return pollAttempts;
        }

        public void setPollAttempts(int pollAttempts) {
            this.pollAttempts = pollAttempts;
        }

        public long getPollIntervalMillis() {
            return pollIntervalMillis;
        }

        public void setPollIntervalMillis(long pollIntervalMillis) {
            this.pollIntervalMillis = pollIntervalMillis;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getResponseTimeoutMs() {
            return responseTimeoutMs;
        }

        public void setResponseTimeoutMs(int responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }

    public static class Lifecycle {
        private long defaultRetentionDays = 0;
        private boolean purgeJobEnabled = true;
        private String purgeJobCron = "0 0 2 * * *";
        private long purgeDeletedAfterDays = 30;

        public long getDefaultRetentionDays() {
            return defaultRetentionDays;
        }

        public void setDefaultRetentionDays(long defaultRetentionDays) {
            this.defaultRetentionDays = defaultRetentionDays;
        }

        public boolean isPurgeJobEnabled() {
            return purgeJobEnabled;
        }

        public void setPurgeJobEnabled(boolean purgeJobEnabled) {
            this.purgeJobEnabled = purgeJobEnabled;
        }

        public String getPurgeJobCron() {
            return purgeJobCron;
        }

        public void setPurgeJobCron(String purgeJobCron) {
            this.purgeJobCron = purgeJobCron;
        }

        public long getPurgeDeletedAfterDays() {
            return purgeDeletedAfterDays;
        }

        public void setPurgeDeletedAfterDays(long purgeDeletedAfterDays) {
            this.purgeDeletedAfterDays = purgeDeletedAfterDays;
        }
    }

    public static class ValidationRule {
        private Long maxFileSizeBytes = 10L * 1024L * 1024L;
        private List<String> allowedExtensions = List.of(".pdf", ".png", ".jpg", ".jpeg", ".webp", ".doc", ".docx");
        private List<String> allowedContentTypes = List.of(
                "application/pdf",
                "image/png",
                "image/jpeg",
                "image/webp",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );

        public Long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(Long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public List<String> getAllowedContentTypes() {
            return allowedContentTypes;
        }

        public void setAllowedContentTypes(List<String> allowedContentTypes) {
            this.allowedContentTypes = allowedContentTypes;
        }

        private ValidationRule merge(ValidationRule fallback) {
            ValidationRule merged = new ValidationRule();
            merged.setMaxFileSizeBytes(maxFileSizeBytes != null ? maxFileSizeBytes : fallback.maxFileSizeBytes);
            merged.setAllowedExtensions(
                    allowedExtensions == null || allowedExtensions.isEmpty()
                            ? fallback.allowedExtensions
                            : allowedExtensions
            );
            merged.setAllowedContentTypes(
                    allowedContentTypes == null || allowedContentTypes.isEmpty()
                            ? fallback.allowedContentTypes
                            : allowedContentTypes
            );
            return merged;
        }
    }

    public static class AwsS3 {
        private String region = "ap-south-1";
        private String bucketName = "railcab-documents";
        private String accessKey;
        private String secretKey;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class Minio extends AwsS3 {
        private String endpoint;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }

    public static class AzureBlob {
        private String connectionString;
        private String containerName = "documents";

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }
    }

    public static class GcpStorage {
        private String bucketName = "railcab-documents";

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }
    }
}
