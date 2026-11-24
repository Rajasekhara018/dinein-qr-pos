package com.railcab.nms.application.usecase.support;

/** Fixed values of the COMMUNICATION audit events NMS publishes to the central audit service. */
public final class CommunicationAuditConstants {

    public static final String EVENT_TYPE = "COMMUNICATION";
    public static final String SOURCE_SERVICE = "rider-nms";
    public static final String FEATURE = "rider-nms";
    public static final String MODULE = "CONFIGURATION";
    public static final String APP_CHANNEL = "SERVICE";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";

    /** Audit event ids allow only A-Z a-z 0-9 _ - and 8-64 characters. */
    public static final String EVENT_ID_SEPARATOR = "_";
    public static final int EVENT_ID_MAX_LENGTH = 64;

    /** The audit service keeps at most this many details attributes; failureReason is cut to this length. */
    public static final int FAILURE_REASON_MAX_LENGTH = 300;

    // details keys (contract with Rider_AuditService)
    public static final String D_CHANNEL = "channel";
    public static final String D_TEMPLATE_CODE = "templateCode";
    public static final String D_ORIGIN_SERVICE = "originService";
    public static final String D_RECIPIENT = "recipient";
    public static final String D_PROVIDER = "provider";
    public static final String D_ATTEMPT = "attempt";
    public static final String D_PROVIDER_STATUS = "providerStatus";
    public static final String D_FAILURE_REASON = "failureReason";
    public static final String D_STATUS = "status";
    public static final String D_ERROR_CODE = "errorCode";
    public static final String D_MESSAGE_ID = "messageId";
    public static final String D_PROVIDER_MESSAGE_ID = "providerMessageId";
    public static final String D_ENTITY_TYPE = "entityType";
    public static final String D_ENTITY_ID = "entityId";

    private CommunicationAuditConstants() {
    }
}
