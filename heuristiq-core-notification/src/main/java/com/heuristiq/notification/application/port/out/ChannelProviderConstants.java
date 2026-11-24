package com.railcab.nms.application.port.out;

/**
 * Fixed vocabulary shared by channel providers and their delivery receipts: the concrete provider names (stored on
 * every attempt and matched by delivery receipts, and used as the {@code nms.provider.*} selection keys), the
 * provider-independent failure codes and the normalised accepted/queued status words. Codes for a plain rejection or
 * an exception stay on {@link ProviderSendResult}.
 */
public final class ChannelProviderConstants {

    // concrete provider names
    public static final String PROVIDER_MAILGUN = "mailgun";
    public static final String PROVIDER_SES = "ses";
    public static final String PROVIDER_SMTP = "smtp";
    public static final String PROVIDER_FCM = "fcm";
    public static final String PROVIDER_DIGIMILE = "digimile";
    public static final String PROVIDER_TWILIO = "twilio";
    public static final String PROVIDER_META = "meta";
    public static final String PROVIDER_EXOTEL = "exotel";
    /** Selection key meaning "channel switched off" ({@code nms.provider.voice=none}). */
    public static final String PROVIDER_NONE = "none";

    // failure codes
    public static final String CODE_RECIPIENT_BLANK = "RECIPIENT_BLANK";
    public static final String CODE_NO_PROVIDER = "NO_PROVIDER";
    public static final String CODE_NOT_CONFIGURED = "NOT_CONFIGURED";

    /** Reason used when a send is attempted with no recipient. */
    public static final String RECIPIENT_BLANK_MESSAGE = "Recipient is blank";

    // provider status recorded on a successful send
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_QUEUED = "queued";

    private ChannelProviderConstants() {
    }
}
