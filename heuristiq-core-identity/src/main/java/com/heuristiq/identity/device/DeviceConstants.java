package com.heuristiq.uam.device;

/** Fixed values of the device feature: fingerprint fallback and the stored location metadata. */
public final class DeviceConstants {

    /** Fingerprint given to a request that sent no User-Agent. */
    public static final String UNKNOWN_DEVICE = "unknown-device";

    /** Device metadata key holding the last recorded location. */
    public static final String LOCATION_METADATA_KEY = "location";

    /** Longest location label kept; longer labels are cut. */
    public static final int LOCATION_LABEL_MAX = 100;

    private DeviceConstants() {
    }
}
