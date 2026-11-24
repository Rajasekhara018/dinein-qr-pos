package com.heuristiq.payment.config;

/** Fixed paths and names of the outbound clients built in {@link RestClientConfig}. */
public final class RestClientConstants {

    /** Base path of the Rider_RideService API. */
    public static final String RIDE_API_PATH = "/api/v1/ride";
    /** Resilient-caller name of Rider_RideService. */
    public static final String RIDE_SERVICE_NAME = "ride-service";

    private RestClientConstants() {
    }
}
