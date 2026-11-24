package com.railcab.nms.adapters.in.web.callback;

import java.util.List;
import java.util.Set;

import com.railcab.nms.adapters.in.web.WebApiConstants;
import com.railcab.nms.application.port.out.ChannelProviderConstants;

/** Wire constants of the inbound provider callbacks: paths, header and parameter names, status vocabularies. */
public final class CallbackConstants {

    /** Base path; the gateway must expose it without a user token (see the NMS report). */
    public static final String BASE_PATH = WebApiConstants.CALLBACKS_PATH;

    public static final String PATH_DIGIMILE_SMS = "/digimile/sms";
    public static final String PATH_GENERIC = "/generic";
    public static final String PATH_TWILIO = "/twilio";
    public static final String PATH_META_WHATSAPP = "/meta/whatsapp";
    public static final String PATH_MAILGUN = "/mailgun";

    public static final String HEADER_SHARED_SECRET = "X-NMS-Callback-Secret";
    public static final String HEADER_TWILIO_SIGNATURE = "X-Twilio-Signature";
    public static final String HEADER_META_SIGNATURE = "X-Hub-Signature-256";

    public static final String PROVIDER_DIGIMILE = ChannelProviderConstants.PROVIDER_DIGIMILE;
    public static final String PROVIDER_TWILIO = ChannelProviderConstants.PROVIDER_TWILIO;
    public static final String PROVIDER_META = ChannelProviderConstants.PROVIDER_META;
    public static final String PROVIDER_MAILGUN = ChannelProviderConstants.PROVIDER_MAILGUN;

    /** Digimile parameter names, matched case-insensitively. UNVERIFIED against a live account. */
    public static final List<String> DIGIMILE_ID_PARAMS = List.of("msgid", "messageid", "message_id", "mid", "id");
    public static final List<String> DIGIMILE_STATUS_PARAMS = List.of("status", "dlrstatus", "dlr_status", "stat");
    public static final List<String> DIGIMILE_ERROR_PARAMS = List.of("err", "errorcode", "error_code", "errcode", "reason");

    public static final Set<String> DIGIMILE_DELIVERED = Set.of("DELIVRD", "DELIVERED", "DELIVERY_SUCCESS", "DEL");
    public static final Set<String> DIGIMILE_SENT = Set.of("ENROUTE", "ACCEPTD", "ACCEPTED", "SUBMITTED", "SENT");
    public static final Set<String> DIGIMILE_FAILED = Set.of("UNDELIV", "UNDELIVERED", "EXPIRED", "DELETED",
            "REJECTD", "REJECTED", "FAILED", "UNKNOWN");

    public static final String TWILIO_PARAM_SID = "MessageSid";
    public static final String TWILIO_PARAM_STATUS = "MessageStatus";
    public static final String TWILIO_PARAM_ERROR_CODE = "ErrorCode";
    public static final String TWILIO_PARAM_ERROR_MESSAGE = "ErrorMessage";

    /** Exotel voice call status callback and IVR passthru (digits). */
    public static final String PATH_VOICE_EXOTEL = "/voice/exotel";
    public static final String PROVIDER_EXOTEL = ChannelProviderConstants.PROVIDER_EXOTEL;
    /** Exotel parameter names, matched case-insensitively; the first present one wins. */
    public static final List<String> EXOTEL_CALL_SID_PARAMS = List.of("CallSid", "Sid");
    public static final List<String> EXOTEL_STATUS_PARAMS = List.of("Status", "CallStatus", "DialCallStatus");
    public static final List<String> EXOTEL_DIGITS_PARAMS = List.of("digits", "Digits");
    public static final String EXOTEL_PARAM_CUSTOM_FIELD = "CustomField";
    /** Keys of the CustomField JSON NMS puts on every call. */
    public static final String CUSTOM_FIELD_ENTITY_TYPE = "entityType";
    public static final String CUSTOM_FIELD_ENTITY_ID = "entityId";
    public static final String CUSTOM_FIELD_EVENT_CODE = "eventCode";

    public static final String META_PARAM_MODE = "hub.mode";
    public static final String META_PARAM_VERIFY_TOKEN = "hub.verify_token";
    public static final String META_PARAM_CHALLENGE = "hub.challenge";
    public static final String META_MODE_SUBSCRIBE = "subscribe";

    private CallbackConstants() {
    }
}
