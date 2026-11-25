package com.heuristiq.notification.adapters.in.web;

/**
 * Wire constants of NMS's inbound HTTP API: the base path of every controller and of the in-app WebSocket endpoint,
 * plus fixed input limits applied at the web edge. Values are compile-time constants so they can be used in
 * mapping annotations. Provider callback paths live in {@code CallbackConstants}.
 */
public final class WebApiConstants {

    /** Common prefix of every NMS endpoint (the gateway's no-rewrite {@code /api/v1/nms/**} route). */
    public static final String API_BASE_PATH = "/api/v1/nms";

    public static final String BROADCASTS_PATH = API_BASE_PATH + "/broadcasts";
    public static final String DEVICES_PATH = API_BASE_PATH + "/devices";
    public static final String NOTIFY_PATH = API_BASE_PATH + "/notify";
    public static final String EVENTS_PATH = API_BASE_PATH + "/events";
    public static final String IN_APP_NOTIFICATIONS_PATH = API_BASE_PATH + "/in-app/notifications";
    public static final String LOGS_PATH = API_BASE_PATH + "/logs";
    public static final String LOG_ATTEMPTS_PATH = LOGS_PATH + "/attempts";
    public static final String MESSAGES_PATH = API_BASE_PATH + "/messages";
    public static final String SEARCH_REINDEX_PATH = API_BASE_PATH + "/search/reindex";
    public static final String TEMPLATES_PATH = API_BASE_PATH + "/templates";
    public static final String CALLBACKS_PATH = API_BASE_PATH + "/callbacks";

    /** STOMP/SockJS endpoint of in-app notifications (not a controller, so registered by the WebSocket config). */
    public static final String IN_APP_WS_PATH = API_BASE_PATH + "/in-app/ws";

    /** Longest device detail (app version, OS, model, locale, time zone) kept when registering a device token. */
    public static final int DEVICE_DETAIL_MAX_LENGTH = 120;

    private WebApiConstants() {
    }
}
