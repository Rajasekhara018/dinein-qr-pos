package com.heuristiq.audit.domain;

/** What kind of fact an audit event records. Stored as its name; the retention class depends on it. */
public enum AuditEventType {
    /** An API call seen by the gateway (who, which privilege, which path, outcome). */
    ACCESS,
    /** A screen opened in the Angular or Flutter clients. */
    SCREEN_VIEW,
    /** One delivery attempt of an outbound notification (reported by NMS). */
    COMMUNICATION,
    /** One call to a payment gateway (reported by the payment service). */
    PAYMENT_GATEWAY,
    /** A business action recorded by a service (approve, refund, config change). */
    DOMAIN
}
