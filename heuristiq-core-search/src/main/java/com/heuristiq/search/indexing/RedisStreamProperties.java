package com.heuristiq.search.indexing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "railcab.search.stream")
public class RedisStreamProperties {
    /** The single shared Redis Stream key that BookingsAPI/AdminAPI/UAM publish index/delete events onto. */
    private String name = "railcab:search:index-stream";
    private String consumerGroup = "search-index-group";
    private String consumerName = "railcab-search-api";
    /** How often the pending-entries recovery job runs. */
    private long recoveryIntervalMs = 60_000;
    /** Minimum time a message must sit unacknowledged before it's eligible to be reclaimed. */
    private long minIdleTimeMs = 60_000;
}
