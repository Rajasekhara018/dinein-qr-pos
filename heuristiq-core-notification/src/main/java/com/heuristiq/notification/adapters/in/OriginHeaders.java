package com.railcab.nms.adapters.in;

/** Optional header (HTTP header or Kafka record header) a producer can use to name itself as the origin of a notification. */
public final class OriginHeaders {

    public static final String SOURCE_SERVICE = "X-Source-Service";

    private OriginHeaders() {
    }
}
