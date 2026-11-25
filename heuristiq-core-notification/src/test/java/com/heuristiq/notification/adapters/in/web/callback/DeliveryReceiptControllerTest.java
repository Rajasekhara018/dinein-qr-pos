package com.heuristiq.notification.adapters.in.web.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.usecase.DeliveryReceiptService;
import com.heuristiq.notification.application.usecase.support.CallbackAuthenticator;
import com.heuristiq.notification.application.usecase.support.CommunicationAuditPublisher;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.testsupport.InMemoryAttemptRepository;
import com.heuristiq.notification.testsupport.InMemoryLogRepository;
import com.heuristiq.notification.testsupport.InMemoryOutboxRepository;

/** Callback endpoints end to end (controller, authenticator, receipt service, in-memory stores). */
class DeliveryReceiptControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "shared-secret";
    private static final String TWILIO_URL = "https://nms.example.com/api/v1/nms/callbacks/twilio";
    private static final String TWILIO_TOKEN = "twilio-auth-token";
    private static final String META_SECRET = "meta-app-secret";
    private static final String META_VERIFY = "meta-verify";
    private static final String MAILGUN_KEY = "mailgun-signing-key";

    private MockMvc mvc;
    private InMemoryAttemptRepository attempts;
    private InMemoryOutboxRepository outbox;
    private NmsProperties props;

    @BeforeEach
    void setUp() {
        props = new NmsProperties();
        props.getCallbacks().setSharedSecret(SECRET);
        props.getCallbacks().setTwilioCallbackUrl(TWILIO_URL);
        props.getTwilio().setAuthToken(TWILIO_TOKEN);
        props.getCallbacks().setMetaAppSecret(META_SECRET);
        props.getCallbacks().setMetaVerifyToken(META_VERIFY);
        props.getCallbacks().setMailgunSigningKey(MAILGUN_KEY);

        attempts = new InMemoryAttemptRepository();
        outbox = new InMemoryOutboxRepository();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DeliveryReceiptService service = new DeliveryReceiptService(attempts, new InMemoryLogRepository(),
                new CommunicationAuditPublisher(outbox, mapper, props, CLOCK), CLOCK);
        mvc = MockMvcBuilders.standaloneSetup(
                new DeliveryReceiptController(service, new CallbackAuthenticator(props), mapper, CLOCK)).build();

        for (String id : new String[] {"dig-1", "SM1", "wamid.1", "mg-1@example.com"}) {
            attempts.save(NotificationAttempt.builder().logId("log-" + id).messageId("m-" + id).channel(NotificationChannel.SMS)
                    .providerMessageId(id).attemptNumber(1).status(DeliveryStatus.SENT).recipient("******3210")
                    .startedAt(LocalDateTime.of(2026, 10, 6, 9, 59)).build());
        }
    }

    private DeliveryStatus statusOf(String providerMessageId) {
        return attempts.findLatestByProviderMessageId(providerMessageId).orElseThrow().getStatus();
    }

    // ── Digimile / generic (shared secret) ───────────────────────────────────

    @Test
    void digimileDlrWithTheSecretUpdatesTheAttempt() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/digimile/sms").header("X-NMS-Callback-Secret", SECRET)
                        .param("msgid", "dig-1").param("status", "DELIVRD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("APPLIED"));

        assertThat(statusOf("dig-1")).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(outbox.all()).hasSize(1);
    }

    @Test
    void digimileAlsoAcceptsAGetBecauseGatewaysDifferOnTheVerb() throws Exception {
        mvc.perform(get("/api/v1/nms/callbacks/digimile/sms").header("X-NMS-Callback-Secret", SECRET)
                        .param("messageId", "dig-1").param("status", "UNDELIV").param("err", "11"))
                .andExpect(status().isOk());

        assertThat(statusOf("dig-1")).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void aDuplicateReceiptIsAcknowledgedAndChangesNothing() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/nms/callbacks/digimile/sms").header("X-NMS-Callback-Secret", SECRET)
                            .param("msgid", "dig-1").param("status", "DELIVRD"))
                    .andExpect(status().isOk());
        }

        assertThat(attempts.findLatestByProviderMessageId("dig-1").orElseThrow().getVersion()).isEqualTo(1L);
        assertThat(outbox.all()).hasSize(1);
    }

    @Test
    void aWrongOrMissingSecretIsRefusedAndNothingChanges() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/digimile/sms").header("X-NMS-Callback-Secret", "wrong")
                        .param("msgid", "dig-1").param("status", "DELIVRD"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/nms/callbacks/digimile/sms").param("msgid", "dig-1").param("status", "DELIVRD"))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf("dig-1")).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void anUnconfiguredSecretRefusesEverythingEvenAnEmptyHeader() throws Exception {
        props.getCallbacks().setSharedSecret("");

        mvc.perform(post("/api/v1/nms/callbacks/digimile/sms").header("X-NMS-Callback-Secret", "")
                        .param("msgid", "dig-1").param("status", "DELIVRD"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownStatusIsAcknowledgedButIgnored() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/digimile/sms").header("X-NMS-Callback-Secret", SECRET)
                        .param("msgid", "dig-1").param("status", "WHATEVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("INVALID"));

        assertThat(statusOf("dig-1")).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void anUnknownProviderMessageIdIsAcknowledged() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/digimile/sms").header("X-NMS-Callback-Secret", SECRET)
                        .param("msgid", "ghost").param("status", "DELIVRD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("UNKNOWN_MESSAGE"));
    }

    @Test
    void theGenericEndpointTakesNmsStatuses() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/generic").header("X-NMS-Callback-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"acme\",\"providerMessageId\":\"dig-1\",\"status\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("APPLIED"));

        assertThat(statusOf("dig-1")).isEqualTo(DeliveryStatus.READ);
    }

    // ── Twilio ───────────────────────────────────────────────────────────────

    private static String twilioSignature(String url, String... nameValuePairsSorted) {
        StringBuilder data = new StringBuilder(url);
        for (String s : nameValuePairsSorted) {
            data.append(s);
        }
        return Base64.getEncoder().encodeToString(hmac("HmacSHA1", TWILIO_TOKEN, data.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void twilioCallbackWithAValidSignatureUpdatesTheAttempt() throws Exception {
        String sig = twilioSignature(TWILIO_URL, "MessageSidSM1", "MessageStatusdelivered");

        mvc.perform(post("/api/v1/nms/callbacks/twilio").header("X-Twilio-Signature", sig)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("MessageSid", "SM1").param("MessageStatus", "delivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("APPLIED"));

        assertThat(statusOf("SM1")).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void twilioCallbackWithATamperedBodyIsRefused() throws Exception {
        String sig = twilioSignature(TWILIO_URL, "MessageSidSM1", "MessageStatusdelivered");

        mvc.perform(post("/api/v1/nms/callbacks/twilio").header("X-Twilio-Signature", sig)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("MessageSid", "SM1").param("MessageStatus", "failed"))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf("SM1")).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void twilioUndeliveredBecomesBouncedWithItsErrorCode() throws Exception {
        String sig = twilioSignature(TWILIO_URL, "ErrorCode30003", "MessageSidSM1", "MessageStatusundelivered");

        mvc.perform(post("/api/v1/nms/callbacks/twilio").header("X-Twilio-Signature", sig)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("MessageSid", "SM1").param("MessageStatus", "undelivered").param("ErrorCode", "30003"))
                .andExpect(status().isOk());

        NotificationAttempt a = attempts.findLatestByProviderMessageId("SM1").orElseThrow();
        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.BOUNCED);
        assertThat(a.getErrorCode()).isEqualTo("30003");
    }

    // ── Meta WhatsApp ────────────────────────────────────────────────────────

    private static String metaSignature(String body) {
        return "sha256=" + HexFormat.of().formatHex(hmac("HmacSHA256", META_SECRET, body.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String META_BODY = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":"
            + "{\"statuses\":[{\"id\":\"wamid.1\",\"status\":\"delivered\",\"timestamp\":\"1791280800\"},"
            + "{\"id\":\"wamid.1\",\"status\":\"read\",\"timestamp\":\"1791280900\"}]}}]}]}";

    @Test
    void metaWebhookWithAValidSignatureAppliesEveryStatusInTheBatch() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/meta/whatsapp").header("X-Hub-Signature-256", metaSignature(META_BODY))
                        .contentType(MediaType.APPLICATION_JSON).content(META_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.applied").value(2));

        assertThat(statusOf("wamid.1")).isEqualTo(DeliveryStatus.READ);
    }

    @Test
    void metaWebhookWithABadSignatureIsRefused() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/meta/whatsapp").header("X-Hub-Signature-256", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON).content(META_BODY))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf("wamid.1")).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void metaFailedStatusCarriesTheErrorCodeAndTitle() throws Exception {
        String body = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.1\",\"status\":\"failed\","
                + "\"errors\":[{\"code\":131026,\"title\":\"Message undeliverable\"}]}]}}]}]}";

        mvc.perform(post("/api/v1/nms/callbacks/meta/whatsapp").header("X-Hub-Signature-256", metaSignature(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        NotificationAttempt a = attempts.findLatestByProviderMessageId("wamid.1").orElseThrow();
        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(a.getErrorCode()).isEqualTo("131026");
        assertThat(a.getErrorMessage()).isEqualTo("Message undeliverable");
    }

    @Test
    void metaSubscriptionHandshakeEchoesTheChallengeOnlyForTheRightToken() throws Exception {
        mvc.perform(get("/api/v1/nms/callbacks/meta/whatsapp").param("hub.mode", "subscribe")
                        .param("hub.verify_token", META_VERIFY).param("hub.challenge", "12345"))
                .andExpect(status().isOk()).andExpect(content().string("12345"));
        mvc.perform(get("/api/v1/nms/callbacks/meta/whatsapp").param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong").param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
    }

    // ── Mailgun ──────────────────────────────────────────────────────────────

    private static String mailgunBody(String event, String severity, String signatureOverride) {
        String timestamp = "1791280800";
        String token = "tok123";
        String signature = signatureOverride != null ? signatureOverride
                : HexFormat.of().formatHex(hmac("HmacSHA256", MAILGUN_KEY, (timestamp + token).getBytes(StandardCharsets.UTF_8)));
        return "{\"signature\":{\"timestamp\":\"" + timestamp + "\",\"token\":\"" + token + "\",\"signature\":\"" + signature + "\"},"
                + "\"event-data\":{\"event\":\"" + event + "\",\"severity\":\"" + severity + "\",\"timestamp\":1791280800.5,"
                + "\"message\":{\"headers\":{\"message-id\":\"mg-1@example.com\"}},"
                + "\"delivery-status\":{\"code\":550,\"message\":\"mailbox full\"}}}";
    }

    @Test
    void mailgunPermanentFailureBouncesTheAttempt() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/mailgun").contentType(MediaType.APPLICATION_JSON)
                        .content(mailgunBody("failed", "permanent", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("APPLIED"));

        NotificationAttempt a = attempts.findLatestByProviderMessageId("mg-1@example.com").orElseThrow();
        assertThat(a.getStatus()).isEqualTo(DeliveryStatus.BOUNCED);
        assertThat(a.getErrorCode()).isEqualTo("550");
    }

    @Test
    void mailgunTemporaryFailureIsIgnoredBecauseMailgunRetriesItself() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/mailgun").contentType(MediaType.APPLICATION_JSON)
                        .content(mailgunBody("failed", "temporary", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("INVALID"));

        assertThat(statusOf("mg-1@example.com")).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void mailgunWithABadSignatureIsRefused() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/mailgun").contentType(MediaType.APPLICATION_JSON)
                        .content(mailgunBody("delivered", "", "00ff")))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf("mg-1@example.com")).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void anUnreadableBodyIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/nms/callbacks/mailgun").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    private static byte[] hmac(String algorithm, String key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
