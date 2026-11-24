package com.railcab.audit.application;

import com.railcab.audit.config.AuditProperties;
import com.railcab.audit.domain.AppChannel;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventType;
import com.railcab.audit.domain.AuditOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventSanitizerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private final AuditProperties props = new AuditProperties();
    private final EventSanitizer sanitizer = new EventSanitizer(props);

    private AuditEvent ok(AuditEventInput in) {
        EventSanitizer.Outcome o = sanitizer.sanitize(in, NOW);
        assertThat(o.error()).as("rejection").isNull();
        return o.event();
    }

    private String error(AuditEventInput in) {
        return sanitizer.sanitize(in, NOW).error();
    }

    @Test
    void validAccessEventKeepsFieldsAndDerivesOutcomeAndUserType() {
        AuditEvent e = ok(InputBuilder.access().eventId("evt-12345678").statusCode(403).role("ROLE_ADMIN")
                .roles(List.of("ROLE_ADMIN", "SUPPORT")).build());
        assertThat(e.getEventId()).isEqualTo("evt-12345678");
        assertThat(e.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(e.getRole()).isEqualTo("ADMIN");
        assertThat(e.getRoles()).containsExactly("ADMIN", "SUPPORT");
        assertThat(e.getUserType()).isEqualTo("STAFF");
        assertThat(e.getEventType()).isEqualTo(AuditEventType.ACCESS);
    }

    @Test
    void generatesEventIdWhenMissingAndInfersCustomerAndDriver() {
        AuditEvent customer = ok(InputBuilder.access().role("CUSTOMER").roles(List.of("CUSTOMER")).build());
        assertThat(customer.getEventId()).hasSize(36);
        assertThat(customer.getUserType()).isEqualTo("CUSTOMER");
        assertThat(ok(InputBuilder.access().role("DRIVER").roles(List.of("DRIVER")).build()).getUserType()).isEqualTo("DRIVER");
        assertThat(ok(InputBuilder.access().role("INTERNAL_SERVICE").roles(List.of("INTERNAL_SERVICE")).build()).getUserType())
                .isEqualTo("SERVICE");
    }

    @Test
    void acceptsTheWalletWebChannel() {
        assertThat(ok(InputBuilder.access().appChannel("WALLET_WEB").build()).getAppChannel()).isEqualTo(AppChannel.WALLET_WEB);
        assertThat(error(InputBuilder.access().appChannel("WALLET_APP").build())).contains("WALLET_WEB");
    }

    @Test
    void rejectsBadInput() {
        assertThat(error(InputBuilder.access().eventType("NOPE").build())).contains("eventType");
        assertThat(error(InputBuilder.access().eventType(null).build())).contains("eventType");
        assertThat(error(InputBuilder.access().sourceService(null).build())).contains("sourceService");
        assertThat(error(InputBuilder.access().eventId("short").build())).contains("eventId");
        assertThat(error(InputBuilder.access().method("FETCH").build())).contains("method");
        assertThat(error(InputBuilder.access().statusCode(1500).build())).contains("statusCode");
        assertThat(error(InputBuilder.access().appChannel("TOASTER").build())).contains("appChannel");
        assertThat(error(InputBuilder.access().outcome("MAYBE").build())).contains("outcome");
        assertThat(error(InputBuilder.access().statusCode(null).outcome(null).build())).contains("outcome or statusCode");
    }

    @Test
    void screenKeyFormatIsValidatedAndRequiredForScreenViews() {
        assertThat(ok(InputBuilder.access().eventType("SCREEN_VIEW").screenKey("payments.refunds").statusCode(null)
                .method(null).path(null).outcome("SUCCESS").build()).getScreenKey()).isEqualTo("payments.refunds");
        for (String bad : List.of("Payments.Refunds", "payments..refunds", "1abc", "pay ments", "a/b", "x".repeat(101))) {
            assertThat(error(InputBuilder.access().eventType("SCREEN_VIEW").screenKey(bad).build()))
                    .as(bad).contains("screenKey");
        }
        assertThat(error(InputBuilder.access().eventType("SCREEN_VIEW").screenKey(null).build())).contains("screenKey is required");
    }

    @Test
    void pathLosesQueryStringAndFragment() {
        assertThat(ok(InputBuilder.access().path("/api/x/y?token=abc&a=1#frag").build()).getPath()).isEqualTo("/api/x/y");
        assertThat(ok(InputBuilder.access().path("/api/x/" + "a".repeat(800)).build()).getPath()).hasSize(500);
    }

    @Test
    void controlCharactersAreStripped() {
        AuditEvent e = ok(InputBuilder.access().userId("u\n1").build());
        assertThat(e.getUserId()).isEqualTo("u 1");
    }

    @Test
    void ipIsMaskedByDefaultAndKeptWhenMaskingIsOff() {
        assertThat(ok(InputBuilder.access().ipAddress("203.0.113.57").build()).getIpAddress()).isEqualTo("203.0.113.0");
        assertThat(ok(InputBuilder.access().ipAddress("2001:db8:1:2:3:4:5:6").build()).getIpAddress()).isEqualTo("2001:db8:1:2::");
        assertThat(ok(InputBuilder.access().ipAddress("not an ip").build()).getIpAddress()).isNull();
        props.getIngest().setMaskIp(false);
        assertThat(ok(InputBuilder.access().ipAddress("203.0.113.57").build()).getIpAddress()).isEqualTo("203.0.113.57");
    }

    @Test
    void futureDatedEventsAreClampedAndMissingDatesDefaultToNow() {
        assertThat(ok(InputBuilder.access().occurredAt(NOW.plusSeconds(3600)).build()).getOccurredAt()).isEqualTo(NOW);
        assertThat(ok(InputBuilder.access().occurredAt(NOW.plusSeconds(60)).build()).getOccurredAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(ok(InputBuilder.access().build()).getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void detailsDropSecretsRedactTokensAndMaskRecipients() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("channel", "SMS");
        d.put("templateCode", "OTP_LOGIN");
        d.put("recipient", "9876543210");
        d.put("recipientEmail", "john.doe@example.com");
        d.put("otp", "123456");
        d.put("accessToken", "abc");
        d.put("apiKey", "k");
        d.put("cardNumber", "4111111111111111");
        d.put("password", "x");
        d.put("providerStatus", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0");
        d.put("note", "Bearer abcdefghijklmnop");
        d.put("nested", Map.of("a", "b"));
        d.put("attempt", 2);
        d.put("bad key!", "x");
        AuditEvent e = ok(InputBuilder.access().eventType("COMMUNICATION").details(d).build());
        assertThat(e.getDetails()).containsEntry("channel", "SMS").containsEntry("templateCode", "OTP_LOGIN")
                .containsEntry("recipient", "******3210").containsEntry("recipientEmail", "j***@example.com")
                .containsEntry("providerStatus", "[redacted]").containsEntry("note", "[redacted]")
                .containsEntry("attempt", "2")
                .doesNotContainKeys("otp", "accessToken", "apiKey", "cardNumber", "password", "nested", "bad key!");
    }

    @Test
    void detailsAreBounded() {
        Map<String, Object> d = new LinkedHashMap<>();
        for (int i = 0; i < 40; i++) {
            d.put("k" + i, "v");
        }
        d.put("long", "x".repeat(2000));
        AuditEvent e = ok(InputBuilder.access().details(d).build());
        assertThat(e.getDetails()).hasSizeLessThanOrEqualTo(20);
        Map<String, Object> d2 = Map.of("long", "x".repeat(2000));
        assertThat(ok(InputBuilder.access().details(d2).build()).getDetails().get("long")).hasSize(500);
    }

    @Test
    void sensitiveKeyDetectionUsesWordsNotSubstrings() {
        assertThat(EventSanitizer.isSensitiveKey("otpCode")).isTrue();
        assertThat(EventSanitizer.isSensitiveKey("refresh_token")).isTrue();
        assertThat(EventSanitizer.isSensitiveKey("X-Api-Key")).isTrue();
        assertThat(EventSanitizer.isSensitiveKey("apiKey")).isTrue();
        assertThat(EventSanitizer.isSensitiveKey("shipping")).isFalse();
        assertThat(EventSanitizer.isSensitiveKey("gatewayRef")).isFalse();
        assertThat(EventSanitizer.isSensitiveKey("pincode")).isFalse();
    }

    @Test
    void maskRecipientHandlesEmailPhoneAndShortValues() {
        assertThat(EventSanitizer.maskRecipient("a@b.com")).isEqualTo("a***@b.com");
        assertThat(EventSanitizer.maskRecipient("+91 98765 43210")).isEqualTo("******3210");
        assertThat(EventSanitizer.maskRecipient("abc")).isEqualTo("a***");
        assertThat(EventSanitizer.maskRecipient("j***@x.com")).isEqualTo("j***@x.com");
    }

    @Test
    void subjectAndReasonAreOptionalAndKeptWhenValid() {
        AuditEvent none = ok(InputBuilder.access().build());
        assertThat(none.getSubjectType()).isNull();
        assertThat(none.getSubjectId()).isNull();
        assertThat(none.getReasonCode()).isNull();
        AuditEvent blank = ok(InputBuilder.access().subjectType("  ").subjectId("").reasonCode(" ").build());
        assertThat(blank.getSubjectType()).isNull();
        assertThat(blank.getSubjectId()).isNull();
        assertThat(blank.getReasonCode()).isNull();
        AuditEvent e = ok(InputBuilder.access().eventType("DOMAIN").subjectType("DOCUMENT").subjectId("doc_01HZX:v-2.1")
                .reasonCode("VIRUS_DETECTED").build());
        assertThat(e.getSubjectType()).isEqualTo("DOCUMENT");
        assertThat(e.getSubjectId()).isEqualTo("doc_01HZX:v-2.1");
        assertThat(e.getReasonCode()).isEqualTo("VIRUS_DETECTED");
        AuditEvent edge = ok(InputBuilder.access().subjectType("A".repeat(40)).subjectId("x".repeat(128))
                .reasonCode("B".repeat(60)).build());
        assertThat(edge.getSubjectType()).hasSize(40);
        assertThat(edge.getSubjectId()).hasSize(128);
        assertThat(edge.getReasonCode()).hasSize(60);
    }

    @Test
    void lowerCaseCodesAreNormalisedToUpperSnake() {
        AuditEvent e = ok(InputBuilder.access().subjectType(" notification_message ").reasonCode("mime_mismatch").build());
        assertThat(e.getSubjectType()).isEqualTo("NOTIFICATION_MESSAGE");
        assertThat(e.getReasonCode()).isEqualTo("MIME_MISMATCH");
    }

    @Test
    void tooLongOrMalformedSubjectAndReasonRejectTheEvent() {
        assertThat(error(InputBuilder.access().subjectType("A".repeat(41)).build())).contains("subjectType");
        assertThat(error(InputBuilder.access().subjectId("x".repeat(129)).build())).contains("subjectId");
        assertThat(error(InputBuilder.access().reasonCode("B".repeat(61)).build())).contains("reasonCode");
        for (String bad : List.of("DOC-UMENT", "1DOC", "DOC UMENT", "DOC'; DROP", "DOC\nX", "{\"$ne\":null}", "DOC.X")) {
            assertThat(error(InputBuilder.access().subjectType(bad).build())).as(bad).contains("subjectType");
            assertThat(error(InputBuilder.access().reasonCode(bad).build())).as(bad).contains("reasonCode");
        }
        for (String bad : List.of("a b", "id'; --", "id\n2", "{\"$gt\":\"\"}", "a/b", "<script>", "a,b")) {
            assertThat(error(InputBuilder.access().subjectId(bad).build())).as(bad).contains("subjectId");
        }
    }
}
