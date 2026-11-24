package com.heuristiq.notification.adapters.out.channel.voice.exotel;

/** Wire values of the Exotel v1 Calls/connect API (call a number and hand it to an IVR flow). */
public final class ExotelConstants {

    /** Connect a call to a flow; the {@code .json} suffix makes Exotel answer in JSON instead of XML. */
    public static final String CONNECT_URL = "https://{subdomain}/v1/Accounts/{sid}/Calls/connect.json";

    /** URL of an account's call flow, handed to Exotel as {@code Url}. */
    public static final String FLOW_URL = "http://my.exotel.com/{sid}/exoml/start_voice/{flowId}";

    public static final String PARAM_FROM = "From";
    public static final String PARAM_CALLER_ID = "CallerId";
    public static final String PARAM_URL = "Url";
    public static final String PARAM_CALL_TYPE = "CallType";
    public static final String PARAM_CUSTOM_FIELD = "CustomField";
    public static final String PARAM_STATUS_CALLBACK = "StatusCallback";
    public static final String PARAM_STATUS_CALLBACK_EVENT = "StatusCallbackEvents[0]";
    public static final String PARAM_TIME_LIMIT = "TimeLimit";
    public static final String PARAM_TIME_OUT = "TimeOut";

    /** Transactional call (not promotional), so it is allowed to numbers on the do-not-disturb list. */
    public static final String CALL_TYPE_TRANSACTIONAL = "trans";
    /** Ask only for the final status of the call. */
    public static final String STATUS_EVENT_TERMINAL = "terminal";

    public static final String JSON_CALL = "Call";
    public static final String JSON_SID = "Sid";
    public static final String JSON_STATUS = "Status";

    /** Error code prefix recorded when Exotel answers with an HTTP error. */
    public static final String ERROR_CODE_PREFIX = "EXOTEL_";
    /** Error code when Exotel accepted the request but returned no call id. */
    public static final String CODE_NO_CALL_ID = "EXOTEL_NO_CALL_ID";

    /** User name put in the status callback URL's basic credentials (the password is the callback shared secret). */
    public static final String CALLBACK_BASIC_USER = "nms";

    private ExotelConstants() {
    }
}
