package com.heuristiq.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import org.springframework.validation.annotation.Validated;

import com.heuristiq.notification.infrastructure.cache.NmsCacheConstants;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "nms")
public class NmsProperties {

    @NestedConfigurationProperty
    private Twilio twilio = new Twilio();

    @NestedConfigurationProperty
    private Meta meta = new Meta();

    @NestedConfigurationProperty
    private Firebase firebase = new Firebase();

    @NestedConfigurationProperty
    private Mailgun mailgun = new Mailgun();

    @NestedConfigurationProperty
    private Ses ses = new Ses();

    @NestedConfigurationProperty
    private Digimile digimile = new Digimile();

    @NestedConfigurationProperty
    private DigimileWhatsApp digimileWhatsapp = new DigimileWhatsApp();

    @NestedConfigurationProperty
    private Provider provider = new Provider();

    @NestedConfigurationProperty
    private Email email = new Email();

    @NestedConfigurationProperty
    private Kafka kafka = new Kafka();

    @NestedConfigurationProperty
    private Retry retry = new Retry();

    @NestedConfigurationProperty
    private Security security = new Security();

    @NestedConfigurationProperty
    private Http http = new Http();

    @NestedConfigurationProperty
    private RateLimit rateLimit = new RateLimit();

    @NestedConfigurationProperty
    private Audit audit = new Audit();

    @NestedConfigurationProperty
    private Idempotency idempotency = new Idempotency();

    @NestedConfigurationProperty
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @NestedConfigurationProperty
    private Integration integration = new Integration();

    @NestedConfigurationProperty
    private Broadcast broadcast = new Broadcast();

    @NestedConfigurationProperty
    private Callbacks callbacks = new Callbacks();

    @NestedConfigurationProperty
    private Branding branding = new Branding();

    @NestedConfigurationProperty
    private TemplateTest templateTest = new TemplateTest();

    /** In-process read caches ({@code nms.cache.*}); validated, see docs/CACHING.md. */
    @Valid
    @NestedConfigurationProperty
    private Cache cache = new Cache();

    // ── Read caches (nms.cache.*) ─────────────────────────────────────────────
    @Data
    public static class Cache {
        /** Master switch ({@code nms.cache.enabled}); false turns every cache into a pass-through. */
        private boolean enabled = true;

        @Valid
        @NestedConfigurationProperty
        private Spec organizationProfile = new Spec(NmsCacheConstants.DEFAULT_PROFILE_TTL_SECONDS,
                NmsCacheConstants.DEFAULT_PROFILE_NOT_FOUND_TTL_SECONDS, NmsCacheConstants.DEFAULT_PROFILE_MAX_SIZE);

        @Valid
        @NestedConfigurationProperty
        private Spec template = new Spec(NmsCacheConstants.DEFAULT_TEMPLATE_TTL_SECONDS,
                NmsCacheConstants.DEFAULT_TEMPLATE_NOT_FOUND_TTL_SECONDS, NmsCacheConstants.DEFAULT_TEMPLATE_MAX_SIZE);

        /** True when the master switch and this cache's own switch are both on. */
        public boolean isActive(Spec spec) {
            return enabled && spec.isEnabled();
        }
    }

    /** One cache's settings. */
    @Data
    public static class Spec {
        /** This cache's own switch. */
        private boolean enabled = true;

        /** Lifetime of a found value, in seconds. */
        @Min(1)
        @Max(NmsCacheConstants.MAX_TTL_SECONDS)
        private long ttlSeconds;

        /** Lifetime of a "nothing found" answer, in seconds (keep it short). */
        @Min(1)
        @Max(NmsCacheConstants.MAX_TTL_SECONDS)
        private long notFoundTtlSeconds;

        /** Most entries kept. */
        @Min(1)
        @Max(NmsCacheConstants.MAX_ENTRIES)
        private long maxSize;

        public Spec() {
        }

        public Spec(long ttlSeconds, long notFoundTtlSeconds, long maxSize) {
            this.ttlSeconds = ttlSeconds;
            this.notFoundTtlSeconds = notFoundTtlSeconds;
            this.maxSize = maxSize;
        }
    }

    // ── "Send a test" from the template editor (nms.template-test.*) ──────────
    @Data
    public static class TemplateTest {
        /** Test messages one signed-in user may send per hour; a guard against using the feature to spam. */
        @Min(1)
        private int maxPerHour = 20;
    }

    // ── Brand text used when the default templates are seeded ─────────────────
    @Data
    public static class Branding {
        /** Product name substituted for "RailCab" in the seeded default templates ({@code nms.branding.name}). */
        private String name = "RealBoom";

        /** Name of the matrimony product used in the matrimony default templates ({@code nms.branding.matrimony-name}). */
        private String matrimonyName = "RealBoom Matrimony";

        /** Support address substituted for the placeholder support address in the seeded default templates. */
        private String supportEmail = "support@realboom.in";

        /** Main brand color (links, header) as a hex value, substituted for the primary colour in the seeds. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String primaryColor = "#0b5fff";

        /** Secondary brand color (task and application headings) as a hex value. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String accentColor = "#0b6e4f";

        /** Subject used when a message arrives without one: "{name} Notification". */
        public String defaultSubject() {
            return name + " Notification";
        }

        /** Tagline shown in the email header ({@code nms.branding.tagline}). */
        private String tagline = "A to Z Real Estate Services";

        /** Hex color substituted for %COLOR_ALERT% in the seeded templates. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String alertColor = "#f57c00";

        /** Hex color substituted for %COLOR_SUCCESS% in the seeded templates. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String successColor = "#2e7d32";

        /** Hex color substituted for %COLOR_APPROVED% in the seeded templates. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String approvedColor = "#166534";

        /** Hex color substituted for %COLOR_DANGER% in the seeded templates. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String dangerColor = "#c62828";

        /** Hex color substituted for %COLOR_WARNING% in the seeded templates. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String warningColor = "#b45309";

        /** Hex color substituted for %BRAND_PRIMARY_TINT% in the seeded templates. */
        @Pattern(regexp = "#[0-9a-fA-F]{3,8}")
        private String primaryTintColor = "#f0f4ff";
    }

    // ── Twilio (SMS + WhatsApp) ───────────────────────────────────────────────
    @Data
    public static class Twilio {
        private String accountSid;
        private String authToken;
        private String smsFrom;
        private String whatsappFrom;
    }

    // ── Meta WhatsApp Cloud API ───────────────────────────────────────────────
    @Data
    public static class Meta {
        @NestedConfigurationProperty
        private WhatsApp whatsapp = new WhatsApp();

        @Data
        public static class WhatsApp {
            private String apiUrl;
            private String phoneNumberId;
            private String token;
        }
    }

    // ── Firebase / FCM ────────────────────────────────────────────────────────
    @Data
    public static class Firebase {
        private boolean enabled = false;
        private String credentialsPath;
        private String projectId;
    }

    // ── Mailgun REST API ──────────────────────────────────────────────────────
    @Data
    public static class Mailgun {
        private String apiKey;
        private String domain;
        private String from = "noreply@railcab.com";
        private String apiUrl = "https://api.mailgun.net/v3";
    }

    // ── AWS SES (email) ───────────────────────────────────────────────────────
    @Data
    public static class Ses {
        private String accessKey;
        private String secretKey;
        private String region = "us-east-1";
        private String fromAddress = "noreply@railcab.com";
        private String fromName = "RailCab";
    }

    // ── Digimile Bulk SMS ─────────────────────────────────────────────────────
    @Data
    public static class Digimile {
        private String username;
        private String password;
        private String url = "https://rslri.connectbind.com:8443/bulksms/bulksms?";
        private String senderId = "RAILCB";
        private String entityId;
    }

    // ── Digimile WhatsApp (distinct product/API from the Digimile bulk-SMS gateway above) ──────
    @Data
    public static class DigimileWhatsApp {
        private String apiKey;
        private String senderId;
        private String apiUrl = "https://api.digimiles.in/whatsapp/api/v2/sendmessage";
    }

    // ── Active provider per channel ───────────────────────────────────────────
    @Data
    public static class Provider {
        /** email: smtp | mailgun | ses */
        private String email = "smtp";
        /** sms: twilio | digimile */
        private String sms = "twilio";
        /** whatsapp: twilio | meta | digimile */
        private String whatsapp = "twilio";
        /** push: fcm */
        private String push = "fcm";
        /** voice: exotel | none. Default none = the VOICE channel is off and attempts are recorded as NOT_CONFIGURED. */
        private String voice = "none";
    }

    /** VOICE channel settings ({@code nms.voice.*}). */
    @Valid
    @NestedConfigurationProperty
    private Voice voice = new Voice();

    // ── VOICE channel (automated IVR calls) ───────────────────────────────────
    @Data
    public static class Voice {
        @Valid
        @NestedConfigurationProperty
        private Exotel exotel = new Exotel();

        /**
         * Exotel Connect API with an IVR flow ({@code nms.voice.exotel.*}). Credentials come from the environment
         * (EXOTEL_ACCOUNT_SID, EXOTEL_API_KEY, EXOTEL_API_TOKEN ...); while any of them is blank calls are recorded as
         * NOT_CONFIGURED and nothing is sent.
         */
        @Data
        public static class Exotel {
            private String accountSid;
            private String apiKey;
            private String apiToken;
            /** API host of the account's cluster, e.g. api.exotel.com (Singapore) or api.in.exotel.com (Mumbai). */
            private String subdomain = "api.exotel.com";
            /** The ExoPhone the customer sees as caller. */
            private String callerId;
            /** Id of the call flow (IVR applet flow) that plays the prompt and collects the digit. */
            private String flowId;
            /**
             * Full public URL Exotel posts call status to (the gateway's /api/v1/nms/callbacks/voice/exotel). When it carries
             * no user info and {@code nms.callbacks.shared-secret} is set, the secret is added as HTTP basic credentials.
             */
            private String statusCallbackUrl;
            @Min(100)
            private int connectTimeoutMs = 3000;
            @Min(100)
            private int readTimeoutMs = 10000;
            /** Seconds Exotel lets the phone ring before giving up. */
            @Min(5)
            @Max(120)
            private int ringTimeoutSeconds = 30;
            /** Longest call, in seconds. */
            @Min(10)
            @Max(3600)
            private int timeLimitSeconds = 120;
        }
    }

    // ── Email shared ──────────────────────────────────────────────────────────
    @Data
    public static class Email {
        private String from = "noreply@railcab.com";
    }

    // ── Kafka ─────────────────────────────────────────────────────────────────
    @Data
    public static class Kafka {
        @NestedConfigurationProperty
        private Topic topic = new Topic();

        @NestedConfigurationProperty
        private Retry retry = new Retry();

        @Data
        public static class Topic {
            private String notificationEvents = "railcab-notification-events";

            /** Topic the answers of automated voice calls are published to (consumed by Rider_RideService). */
            private String voiceResponses = "nms-voice-responses";

            @Min(1)
            private int partitions = 3;

            @Min(1)
            private short replicationFactor = 1;
        }

        @Data
        public static class Retry {
            @Min(1)
            private long attempts = 3;

            @Min(0)
            private long backoffMs = 1000;
        }
    }

    @Data
    public static class Retry {
        @Min(1)
        private int attempts = 3;

        @Min(0)
        private long backoffMs = 500;
    }

    @Data
    public static class Security {
        private boolean enabled = false;

        private String internalApiKey;

        private boolean exposeDocs = false;

    }

    @Data
    public static class Http {
        @Min(1)
        private int connectTimeoutMs = 5000;

        @Min(1)
        private int readTimeoutMs = 10000;

        @Min(1)
        private int maxConnections = 100;
    }

    @Data
    public static class RateLimit {
        @Min(1)
        private int emailConcurrency = 20;

        @Min(1)
        private int smsConcurrency = 10;

        @Min(1)
        private int whatsappConcurrency = 10;

        @Min(1)
        private int pushConcurrency = 50;

        @Min(1)
        private int inAppConcurrency = 50;

        /** Concurrent voice calls being placed (bulkhead of the VOICE channel). */
        @Min(1)
        private int voiceConcurrency = 5;
    }

    @Data
    public static class Audit {
        private boolean storeRenderedBody = false;

        private boolean maskRecipients = true;

        @Min(1)
        private int logRetentionDays = 90;

        /** Push of COMMUNICATION events to the central audit service ({@code nms.audit.publish.*}). */
        @NestedConfigurationProperty
        private Publish publish = new Publish();

        @Data
        public static class Publish {
            /** Queue and push communication audit events. When false nothing is queued. */
            private boolean enabled = true;

            /** Base URL of the audit service (Eureka name REALBOOM-AUDITSERVICE when behind a load-balanced client). */
            private String url = "http://localhost:9106";

            /** Shared service credential sent as X-Rider-Service-Token (RIDER_INTERNAL_SERVICE_TOKEN). */
            private String serviceToken;

            @Min(1)
            private int batchSize = 100;

            /** Delay between drain runs. */
            @Min(100)
            private long intervalMs = 5000;

            /** How long one instance owns a queued event while sending it. */
            @Min(1)
            private int leaseSeconds = 60;

            /** Tries before an event is marked DEAD. */
            @Min(1)
            private int maxAttempts = 20;

            @Min(1)
            private int retryInitialBackoffSeconds = 15;

            @Min(1)
            private int retryMaxBackoffSeconds = 900;

            /** Queued events older than this are dropped whether or not they were sent. */
            @Min(1)
            private int outboxRetentionDays = 7;

            @Min(100)
            private long callTimeoutMs = 3000;

            @Min(1)
            private int callMaxAttempts = 3;
        }
    }

    /**
     * Inbound provider delivery-receipt callbacks ({@code nms.callbacks.*}). Every endpoint refuses
     * requests while its own secret is blank, so nothing is open until an operator configures it.
     */
    @Data
    public static class Callbacks {
        /** Shared secret expected in X-NMS-Callback-Secret for the Digimile and generic endpoints. */
        private String sharedSecret;

        /** Full public URL Twilio posts to (used for the signature check and as the per-message StatusCallback). */
        private String twilioCallbackUrl;

        /** Meta app secret: key of the X-Hub-Signature-256 HMAC. */
        private String metaAppSecret;

        /** Token Meta echoes during the webhook subscription handshake. */
        private String metaVerifyToken;

        /** Mailgun HTTP webhook signing key. */
        private String mailgunSigningKey;

        /**
         * Voice-only callback secret (Exotel): carried as basic credentials in the callback URL Exotel stores, so it is never
         * the shared secret of the other receipt endpoints. Empty = voice callbacks refused.
         */
        private String voiceSharedSecret;
    }

    @Data
    public static class Idempotency {
        private boolean retryFailedMessages = true;

        @Min(1)
        private long processingTimeoutMinutes = 30;

        @Min(1)
        private int messageRetentionDays = 30;
    }

    @Data
    public static class CircuitBreaker {
        private boolean enabled = true;

        @Min(1)
        private int failureThreshold = 5;

        @Min(1)
        private long resetTimeoutMs = 60000;
    }

    // ── Outbound calls to sibling services (currently just UAM, for broadcast audience
    // resolution -- see integration.uam.UamUserClient) ─────────────────────────────────────
    @Data
    public static class Integration {
        /** Routed through the gateway's /api/uam/** route, same as every other UAM caller. */
        private String uamBaseUrl = "http://localhost:9101/api/uam";
    }

    @Data
    public static class Broadcast {
        @Min(1)
        private long dispatchFixedDelayMs = 60000;

        @Min(1)
        private int maxPagesPerSweep = 20;

        @Min(1)
        private int pageSize = 200;
    }
}
