package com.railcab.nms.application.usecase.support;

/** Origin values NMS stamps on notifications it starts itself. */
public final class NmsOriginConstants {

    /** Source service of notifications created by NMS broadcasts. */
    public static final String BROADCAST_SOURCE_SERVICE = "rider-nms";

    /** Entity type of a broadcast fan-out; the entity id is the broadcast id. */
    public static final String BROADCAST_ENTITY_TYPE = "BROADCAST";

    private NmsOriginConstants() {
    }
}
