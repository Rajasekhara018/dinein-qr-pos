package com.railcab.dms.scan.metadefender;

/** Wire and wiring values of the MetaDefender Cloud v4 scan adapter. */
public final class MetadefenderConstants {

    private MetadefenderConstants() {
    }

    /** Provider name selecting this adapter; also the resilience4j circuit-breaker / retry instance name. */
    public static final String PROVIDER = "metadefender";
    public static final String WEB_CLIENT_BEAN = "metadefenderWebClient";
    public static final String CONNECTION_POOL_NAME = "metadefender-scan-pool";
    public static final int MAX_CONNECTIONS = 10;

    // ---- MetaDefender API
    public static final String API_KEY_HEADER = "apikey";
    public static final String FILE_PART = "file";
    /** File name sent when the upload carries none. */
    public static final String DEFAULT_FILE_NAME = "document";
    public static final String FIELD_DATA_ID = "data_id";
    public static final String FIELD_PROCESS_INFO = "process_info";
    public static final String FIELD_PROGRESS_PERCENTAGE = "progress_percentage";
    public static final String FIELD_RESULT = "result";
    public static final int PROGRESS_COMPLETE = 100;
    /** Results that count as clean. */
    public static final String RESULT_ALLOWED = "Allowed";
    public static final String RESULT_NO_THREAT = "No Threat Detected";
}
