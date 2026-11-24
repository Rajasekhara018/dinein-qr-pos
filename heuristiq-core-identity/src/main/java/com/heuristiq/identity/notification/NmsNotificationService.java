package com.heuristiq.uam.notification;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.uam.notification.dto.CommonNmsRequest;
import com.heuristiq.uam.notification.dto.NmsNotificationRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resilience: every call goes through a {@link ResilientCaller} (timeout, bulkhead, circuit breaker,
 * exponential backoff with jitter; only transient failures -- timeout, connection error, 429/502/503/504 --
 * are retried, a 4xx is a real answer and is never retried). Calls are treated as
 * {@link Idempotency#SAFE}: NMS reserves the messageID with a unique insert on arrival
 * (NotificationMessageService.reserve) and ignores a repeat as a duplicate, and the id is computed ONCE per
 * logical notification (not per attempt), so a retry after an ambiguous timeout cannot double-send.
 * When a notification is finally dropped an ERROR with the reason is logged -- for an OTP that means the
 * user will not receive their code.
 * <p>
 * Fire-and-forget client for 2026_RailCab_NMS's {@code /api/v1/nms/notify}. Mirrors BookingsAPI's
 * NmsNotificationService: never blocks the caller's request thread and never propagates a
 * failure back into the auth flow -- a slow/unreachable NMS must not break login/registration.
 */
@Slf4j
@Service
public class NmsNotificationService {

    private final WebClient nmsClient;
    private final String nmsInternalApiKey;
    private final ResilientCaller nmsCaller;

    public NmsNotificationService(
            @Value("${railcab.nms.base-url:http://localhost:9111}") String nmsBaseUrl,
            @Value("${railcab.nms.internal-api-key:}") String nmsInternalApiKey,
            WebClient.Builder webClientBuilder,
            @Qualifier("nmsResilientCaller") ResilientCaller nmsCaller) {
        this.nmsClient = webClientBuilder.baseUrl(nmsBaseUrl)
                .filter(CorrelationIdPropagation.filter())
                .build();
        this.nmsInternalApiKey = nmsInternalApiKey;
        this.nmsCaller = nmsCaller;
    }

    @Async(UamCommonConstants.NOTIFICATION_EXECUTOR)
    public void sendOtp(String toEmail, String toPhone, String passengerName, String otp,
                        int expiryMinutes, String purpose) {
        Map<String, String> variables = new HashMap<>();
        variables.put(NmsNotificationConstants.VAR_OTP, otp);
        variables.put(NmsNotificationConstants.VAR_PASSENGER_NAME, passengerName);
        variables.put(NmsNotificationConstants.VAR_EXPIRY_MINUTES, String.valueOf(expiryMinutes));
        variables.put(NmsNotificationConstants.VAR_PURPOSE, purpose);
        try {
            notifyNms(NmsNotificationConstants.EVENT_OTP_REQUEST, toEmail != null ? List.of(toEmail) : null, toPhone, variables);
        } catch (RuntimeException e) {
            // The user will not receive their code -- make that visible (no OTP / full recipient in the log).
            log.error("OTP notification DROPPED, user will not receive the code: purpose={}, recipient={}, reason={}",
                    purpose, mask(toEmail != null ? toEmail : toPhone), describe(e), e);
        }
    }

    /**
     * Tells the first administrator of a new organization that it is ready, with how to sign in. The message is sent as the
     * organization, so it carries the organization's name; {@code signInDetails} is a ready sentence (the one-time password
     * for a new account, or how to sign in for an account that already existed).
     */
    @Async(UamCommonConstants.NOTIFICATION_EXECUTOR)
    public void sendOrganizationAdminWelcome(String toEmail, String toPhone, String adminName, String organizationCode,
                                             String tenantId, String signInDetails) {
        Map<String, String> variables = new HashMap<>();
        variables.put(NmsNotificationConstants.VAR_ADMIN_NAME, adminName);
        variables.put(NmsNotificationConstants.VAR_SIGN_IN_DETAILS, signInDetails);
        variables.put(NmsNotificationConstants.VAR_EMAIL, toEmail);
        variables.put(NmsNotificationConstants.VAR_ORGANIZATION_CODE, organizationCode);
        try {
            notifyNms(NmsNotificationConstants.EVENT_ORGANIZATION_ADMIN_WELCOME, toEmail != null ? List.of(toEmail) : null, toPhone, variables, tenantId);
        } catch (RuntimeException e) {
            log.warn("Organization administrator welcome dropped: recipient={}, reason={}", mask(toEmail), describe(e));
        }
    }

    @Async(UamCommonConstants.NOTIFICATION_EXECUTOR)
    public void sendAccountCreated(String toEmail, String passengerName, String phone) {
        Map<String, String> variables = new HashMap<>();
        variables.put(NmsNotificationConstants.VAR_PASSENGER_NAME, passengerName);
        variables.put(NmsNotificationConstants.VAR_EMAIL, toEmail);
        variables.put(NmsNotificationConstants.VAR_PHONE, phone);
        variables.put(NmsNotificationConstants.VAR_VERIFICATION_LINK, "");
        try {
            notifyNms(NmsNotificationConstants.EVENT_ACCOUNT_CREATED, toEmail != null ? List.of(toEmail) : null, null, variables);
        } catch (RuntimeException e) {
            log.warn("Account-created notification dropped: recipient={}, reason={}", mask(toEmail), describe(e));
        }
    }

    /**
     * Tells the owner of an address that someone tried to create an account with it and that one already exists, so a sign-up
     * form never has to say so on screen. No code and no password in it; the origin names the application it was asked from.
     * Off the request thread like the one-time-code email, so the sign-up answer takes the same time either way.
     */
    @Async(UamCommonConstants.NOTIFICATION_EXECUTOR)
    public void sendSelfRegisterExistingAccount(String toEmail, String applicationCode, String messageId) {
        Map<String, String> variables = new HashMap<>();
        variables.put(NmsNotificationConstants.VAR_EMAIL, toEmail);
        sendWithOrigin(NmsNotificationConstants.EVENT_SELF_REGISTER_EXISTING_ACCOUNT, toEmail, variables, null, null,
                "SELF_REGISTRATION", applicationCode, messageId);
    }

    /**
     * Sends one organization-scoped message with a full origin (source service, event code, triggering user, entity and the
     * current correlation id), synchronously, and says whether NMS accepted it. Used by the readiness alerts, which decide
     * what to remember from the answer. {@code messageId} must identify ONE logical message so a retry is a duplicate.
     *
     * @return true when NMS accepted the message; false when it was dropped (the reason is logged, the address masked)
     */
    public boolean sendWithOrigin(String event, String toEmail, Map<String, String> variables, String tenantId,
                                  String triggeringUserId, String entityType, String entityId, String messageId) {
        if (toEmail == null || toEmail.isBlank()) {
            return false;
        }
        NmsNotificationRequest notification = new NmsNotificationRequest(event, variables, List.of(toEmail), null, null, null,
                NmsNotificationConstants.SOURCE_SERVICE, event, tenantId, triggeringUserId, entityType, entityId,
                org.slf4j.MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY));
        CommonNmsRequest commonRequest = new CommonNmsRequest(messageId, notification);
        try {
            nmsCaller.callBlocking(() -> {
                WebClient.RequestBodySpec request = nmsClient.post().uri(NmsNotificationConstants.NOTIFY_PATH);
                if (nmsInternalApiKey != null && !nmsInternalApiKey.isBlank()) {
                    request.header(NmsNotificationConstants.API_KEY_HEADER, nmsInternalApiKey);
                }
                return request.bodyValue(commonRequest).retrieve().bodyToMono(String.class).defaultIfEmpty("");
            }, Idempotency.SAFE);
            return true;
        } catch (RuntimeException e) {
            log.warn("NMS notification dropped: event={}, recipient={}, reason={}", event, mask(toEmail), describe(e));
            return false;
        }
    }

    /** Why a notification was finally dropped: the downstream reason when NMS was unavailable, else the error. */
    static String describe(Throwable t) {
        if (t instanceof DownstreamUnavailableException d) {
            return "NMS unavailable (" + d.reason() + ")";
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    /** Keeps just enough of an address to correlate a log line without writing PII in full. */
    static String mask(String value) {
        if (value == null || value.isBlank()) return "n/a";
        int at = value.indexOf('@');
        String head = at > 0 ? value.substring(0, Math.min(2, at)) : value.substring(0, Math.min(2, value.length()));
        return head + "***" + (at > 0 ? value.substring(at) : "");
    }

    private void notifyNms(String event, List<String> toEmail, String toPhone, Map<String, String> variables) {
        notifyNms(event, toEmail, toPhone, variables, null);
    }

    private void notifyNms(String event, List<String> toEmail, String toPhone, Map<String, String> variables, String tenantId) {
        if ((toEmail == null || toEmail.isEmpty()) && toPhone == null) return;

        NmsNotificationRequest notificationRequest =
                new NmsNotificationRequest(event, variables, toEmail, toPhone, null, null,
                        NmsNotificationConstants.SOURCE_SERVICE, event, tenantId);
        CommonNmsRequest commonRequest = new CommonNmsRequest(
                messageId(event, toEmail, toPhone, variables, System.currentTimeMillis() / 60_000L), notificationRequest);

        // Resolved once so every retry carries the same messageID and NMS can drop a repeat.
        nmsCaller.callBlocking(() -> {
            WebClient.RequestBodySpec request = nmsClient.post().uri(NmsNotificationConstants.NOTIFY_PATH);
            if (nmsInternalApiKey != null && !nmsInternalApiKey.isBlank()) {
                request.header(NmsNotificationConstants.API_KEY_HEADER, nmsInternalApiKey);
            }
            return request.bodyValue(commonRequest).retrieve().bodyToMono(String.class)
                    .defaultIfEmpty("");
        }, Idempotency.SAFE);
        log.debug("NMS notification submitted: event={}", event);
    }

    /**
     * NMS treats the message id as an idempotency key (unique constraint -- a repeat is silently
     * dropped as a "duplicate"), so it must identify ONE logical notification, not one recipient.
     *
     * The old id for OTPs was "uam:OTP_REQUEST:" + the user's name, identical for every OTP sent
     * to that person: their first OTP went out and every later one was ignored. An OTP's id now
     * also carries a short hash of the OTP itself (+ purpose) and the current minute -- a new OTP
     * is a new message, while the resilience4j retry re-sending the SAME OTP within moments still
     * collapses into one, which is the only duplicate worth suppressing.
     *
     * Events with no OTP (ACCOUNT_CREATED, ...) keep the previous recipient-based id: one per account.
     */
    static String messageId(String event, List<String> toEmail, String toPhone,
                            Map<String, String> variables, long epochMinute) {
        String otp = variables != null ? variables.get(NmsNotificationConstants.VAR_OTP) : null;
        if (otp != null) {
            String recipient = toEmail != null && !toEmail.isEmpty() ? toEmail.get(0) : String.valueOf(toPhone);
            String purpose = variables.getOrDefault(NmsNotificationConstants.VAR_PURPOSE, "");
            return NmsNotificationConstants.MESSAGE_ID_PREFIX + ":" + event + ":" + recipient + ":" + shortHash(otp + "|" + purpose) + ":" + epochMinute;
        }
        String key = variables != null ? variables.getOrDefault(NmsNotificationConstants.VAR_EMAIL, variables.get(NmsNotificationConstants.VAR_PASSENGER_NAME)) : null;
        // The same person can be made administrator of several organizations: one message per organization.
        String organization = variables != null ? variables.get(NmsNotificationConstants.VAR_ORGANIZATION_CODE) : null;
        return NmsNotificationConstants.MESSAGE_ID_PREFIX + ":" + event + ":" + (key != null ? key : System.nanoTime()) + (organization != null ? ":" + organization : "");
    }

    /** First 8 hex chars of SHA-256 -- enough to tell two OTPs apart; the OTP itself is never put in the id. */
    private static String shortHash(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance(UamCommonConstants.SHA_256)
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(System.nanoTime());
        }
    }
}
