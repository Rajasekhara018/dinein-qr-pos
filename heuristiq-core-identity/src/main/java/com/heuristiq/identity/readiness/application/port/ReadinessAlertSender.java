package com.heuristiq.uam.readiness.application.port;

import java.util.Map;

/** Sends one alert message to one person through NMS. */
public interface ReadinessAlertSender {

    /**
     * @param event         base NMS event code
     * @param recipient     who gets it (its language picks the template variant)
     * @param tenantId      organization the alert is about
     * @param variables     template variables
     * @param entityId      origin: the entity the alert is about ({@code tenantId:applicationCode})
     * @param triggeredBy   origin: user whose action triggered it, or null for the scheduler
     * @param alertId       one id per logical alert, shared by all its recipients, so a retry is a duplicate for NMS
     * @return true when NMS accepted the message; false when it could not be delivered (already logged)
     */
    boolean send(String event, AlertRecipient recipient, String tenantId, Map<String, String> variables, String entityId,
                 String triggeredBy, String alertId);
}
