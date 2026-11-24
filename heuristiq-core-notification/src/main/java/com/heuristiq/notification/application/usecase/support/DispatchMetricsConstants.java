package com.railcab.nms.application.usecase.support;

/** Micrometer metric names, tag keys and failure-reason buckets recorded for dispatch attempts. */
final class DispatchMetricsConstants {

    static final String PROVIDER_SEND_DURATION = "nms.provider.send.duration";
    static final String PROVIDER_SEND_TOTAL = "nms.provider.send.total";
    static final String DISPATCH_SKIPPED_TOTAL = "nms.dispatch.skipped.total";

    static final String TAG_CHANNEL = "channel";
    static final String TAG_PROVIDER = "provider";
    static final String TAG_SUCCESS = "success";
    static final String TAG_REASON = "reason";

    // bucketed failure reasons (bounded tag cardinality)
    static final String REASON_NONE = "none";
    static final String REASON_UNKNOWN = "unknown";
    static final String REASON_CIRCUIT_OPEN = "circuit_open";
    static final String REASON_BULKHEAD_FULL = "bulkhead_full";
    static final String REASON_PROVIDER_REJECTED = "provider_rejected";
    static final String REASON_EXCEPTION = "exception";

    private DispatchMetricsConstants() {
    }
}
