package com.railcab.nms.adapters.out.channel;

/** Wire values of the outbound channel provider adapters (vendor response codes, address prefixes). */
public final class ChannelAdapterConstants {

    /** Digimile response code meaning "accepted by the gateway". */
    public static final String DIGIMILE_SUCCESS_CODE = "1701";
    /** Prefix of the error code recorded when Digimile answers with any other code. */
    public static final String DIGIMILE_ERROR_CODE_PREFIX = "DIGIMILE_";
    /** Suffix used when Digimile answered with no code at all. */
    public static final String DIGIMILE_NO_CODE = "NO_CODE";

    /** Twilio's WhatsApp address prefix. */
    public static final String TWILIO_WHATSAPP_PREFIX = "whatsapp:";
    /** Twilio's public WhatsApp sandbox number, used when no sender is configured. */
    public static final String TWILIO_WHATSAPP_SANDBOX_FROM = TWILIO_WHATSAPP_PREFIX + "+14155238886";

    /** STOMP user destination in-app notifications are pushed to. */
    public static final String IN_APP_USER_DESTINATION = "/queue/notifications";

    /** Android notification channel id of ride pushes; both mobile apps create it with high importance at startup. */
    public static final String FCM_RIDE_CHANNEL_ID = "ride_updates";

    private ChannelAdapterConstants() {
    }
}
