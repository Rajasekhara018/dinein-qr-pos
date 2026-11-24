package com.railcab.nms.domain.model;

public enum BroadcastStatus {
    PENDING,
    /** Audience resolved and fan-out in progress (set before the first recipient is dispatched). */
    SENDING,
    SENT,
    CANCELLED,
    FAILED
}
