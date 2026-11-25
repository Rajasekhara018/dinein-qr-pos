package com.heuristiq.identity.readiness.adapter.out;

import java.util.Map;

import com.heuristiq.identity.readiness.application.port.AlertRecipient;
import com.heuristiq.identity.readiness.application.port.ReadinessAlertSender;

/**
 * Readiness alerts are not delivered: NMS no longer has templates for the application go-live events. The alert logic
 * (throttling, recipients) still runs; nothing is sent, so every call reports "not delivered".
 */
public class NoopReadinessAlertSender implements ReadinessAlertSender {

    @Override
    public boolean send(String event, AlertRecipient recipient, String tenantId, Map<String, String> variables, String entityId,
                        String triggeredBy, String alertId) {
        return false;
    }
}
