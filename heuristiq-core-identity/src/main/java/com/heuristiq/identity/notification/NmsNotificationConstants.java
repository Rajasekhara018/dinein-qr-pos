package com.heuristiq.identity.notification;

/** Fixed values for outbound NMS notifications. */
public final class NmsNotificationConstants {

    /** Origin sent to NMS so UAM messages show their source in the communication log. */
    public static final String SOURCE_SERVICE = "uam";

    /** NMS endpoint that accepts a notification request. */
    public static final String NOTIFY_PATH = "/api/v1/nms/notify";

    /** Header carrying NMS's internal API key, sent only when a key is configured. */
    public static final String API_KEY_HEADER = "X-NMS-API-Key";

    /** First segment of every message id UAM sends (NMS's idempotency key). */
    public static final String MESSAGE_ID_PREFIX = "uam";

    /** NMS event (template) codes sent by UAM. */
    public static final String EVENT_OTP_REQUEST = "OTP_REQUEST";
    public static final String EVENT_ORGANIZATION_ADMIN_WELCOME = "ORGANIZATION_ADMIN_WELCOME";
    public static final String EVENT_ACCOUNT_CREATED = "ACCOUNT_CREATED";
    /** Told to someone who tried to create an account with an email that already has one. */
    public static final String EVENT_SELF_REGISTER_EXISTING_ACCOUNT = "MATRIMONY_SELF_REGISTER_EXISTING_ACCOUNT";

    /** Template variable names; they must match the NMS templates. */
    public static final String VAR_OTP = "otp";
    public static final String VAR_PASSENGER_NAME = "passengerName";
    public static final String VAR_EXPIRY_MINUTES = "expiryMinutes";
    public static final String VAR_PURPOSE = "purpose";
    public static final String VAR_ADMIN_NAME = "adminName";
    public static final String VAR_SIGN_IN_DETAILS = "signInDetails";
    public static final String VAR_EMAIL = "email";
    public static final String VAR_ORGANIZATION_CODE = "organizationCode";
    public static final String VAR_PHONE = "phone";
    public static final String VAR_VERIFICATION_LINK = "verificationLink";

    private NmsNotificationConstants() {
    }
}
